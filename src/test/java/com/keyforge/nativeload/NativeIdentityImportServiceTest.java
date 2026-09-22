package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Orchestration tests for {@link NativeIdentityImportService} using fakes — no live IIQ and no
 * database. Covers the extraction summary, per-record failure isolation, the deletion keep-set and
 * sweep, and the empty-source safety behaviour.
 */
class NativeIdentityImportServiceTest {

    private static final String HEX1 = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String HEX2 = "1a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String HEX3 = "2a1b2c3d4e5f60718293a4b5c6d7e8f9";

    /** Serves canned JSON pages by page index (start / pageSize). */
    private static final class FakePages implements NativeIdentityPageSource {
        private final List<String> pages;
        final List<Integer> starts = new ArrayList<>();
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            starts.add(start);
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"rows\":[]}";
        }
    }

    /** Records upserts + the swept keep-set; can be told to fail specific source ids. */
    private static final class FakeSink implements NativeIdentitySink {
        final List<String> upsertedSourceIds = new ArrayList<>();
        final Set<String> failOn = new HashSet<>();
        Collection<String> sweptKeep;
        boolean ensured;
        @Override public void ensure() { ensured = true; }
        @Override public NativeIdentityRepository.UpsertOutcome upsert(NativeIdentityRecord rec) throws SQLException {
            if (failOn.contains(rec.sourceId)) {
                throw new SQLException("boom " + rec.sourceId);
            }
            upsertedSourceIds.add(rec.sourceId);
            return NativeIdentityRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.sweptKeep = keep;
            if (keep.isEmpty()) {
                return new SoftDeleteSweeper.SweepResult("kf_identity", 0, 0, 0, true, "empty");
            }
            return new SoftDeleteSweeper.SweepResult("kf_identity", keep.size(), 2, 1, false, null);
        }
    }

    private static String page(String... hexIds) {
        StringBuilder sb = new StringBuilder("{\"rows\":[");
        for (int i = 0; i < hexIds.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"sourceId\":\"").append(hexIds[i]).append("\",\"name\":\"n").append(i).append("\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void paginatesUpsertsAndSweepsWithKeepSet() throws SQLException {
        FakePages src = new FakePages(List.of(page(HEX1, HEX2), page(HEX3)));
        FakeSink sink = new FakeSink();
        NativeIdentityImportService.Result r =
                new NativeIdentityImportService(src, sink, 2, true).importAll();

        assertTrue(sink.ensured);
        assertEquals(3, r.getExtracted());
        assertEquals(3, r.getPersisted());
        assertEquals(3, r.getInserted());
        assertEquals(0, r.getFailed());
        assertEquals(List.of(0, 2), src.starts, "should page start=0 then start=2, then stop on short page");
        assertTrue(r.isSweepRan());
        assertFalse(r.isSweepSkipped());
        assertEquals(3, sink.sweptKeep.size(), "keep-set = every source id seen");
        assertEquals(2, r.getMarkedDeleted());
        assertEquals(1, r.getRevived());
    }

    @Test
    void isolatesRowFailuresButStillKeepsFailedIdInSweepSet() throws SQLException {
        FakePages src = new FakePages(List.of(page(HEX1, HEX2, HEX3)));
        FakeSink sink = new FakeSink();
        sink.failOn.add(HEX2);
        NativeIdentityImportService.Result r =
                new NativeIdentityImportService(src, sink, 100, true).importAll();

        assertEquals(3, r.getExtracted());
        assertEquals(1, r.getFailed());
        assertEquals(2, r.getPersisted());
        assertEquals(1, r.getFailures().size());
        assertEquals(3, sink.sweptKeep.size(), "a row that failed to upsert must still stay in the keep-set");
    }

    @Test
    void emptySourceRunsSweepButSafetyGuardSkipsIt() throws SQLException {
        FakePages src = new FakePages(List.of("{\"rows\":[]}"));
        FakeSink sink = new FakeSink();
        NativeIdentityImportService.Result r =
                new NativeIdentityImportService(src, sink, 100, true).importAll();

        assertEquals(0, r.getExtracted());
        assertTrue(r.isSweepRan());
        assertTrue(r.isSweepSkipped(), "empty source must not mark anything deleted");
        assertEquals(0, r.getMarkedDeleted());
    }

    @Test
    void sweepDisabledDoesNotSweep() throws SQLException {
        FakePages src = new FakePages(List.of(page(HEX1)));
        FakeSink sink = new FakeSink();
        NativeIdentityImportService.Result r =
                new NativeIdentityImportService(src, sink, 100, false).importAll();

        assertEquals(1, r.getPersisted());
        assertFalse(r.isSweepRan());
        assertEquals(null, sink.sweptKeep);
    }
}
