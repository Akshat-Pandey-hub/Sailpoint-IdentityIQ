package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration for {@link NativeViolationImportService} using fakes — no IIQ, no DB. */
class NativeViolationImportServiceTest {

    private static final String HEX1 = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String HEX2 = "1a1b2c3d4e5f60718293a4b5c6d7e8f9";

    private static final class FakePages implements NativeViolationPageSource {
        private final List<String> pages;
        final List<Integer> starts = new ArrayList<>();
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            starts.add(start);
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"rows\":[]}";
        }
    }

    private static final class FakeSink implements NativeViolationSink {
        Collection<String> sweptKeep;
        boolean ensured;
        @Override public void ensure() { ensured = true; }
        @Override public NativeViolationRepository.UpsertOutcome upsert(NativeViolationRecord rec) {
            return NativeViolationRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.sweptKeep = keep;
            if (keep.isEmpty()) {
                return new SoftDeleteSweeper.SweepResult("kf_violation", 0, 0, 0, true, "empty");
            }
            return new SoftDeleteSweeper.SweepResult("kf_violation", keep.size(), 0, 0, false, null);
        }
    }

    private static String page(String... ids) {
        StringBuilder sb = new StringBuilder("{\"rows\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"sourceId\":\"").append(ids[i]).append("\",\"policyId\":\"p\",\"status\":\"Open\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void paginatesUpsertsAndSweeps() throws SQLException {
        FakePages src = new FakePages(List.of(page(HEX1), page(HEX2)));
        FakeSink sink = new FakeSink();
        NativeViolationImportService.Result r = new NativeViolationImportService(src, sink, 1, true).importAll();
        assertTrue(sink.ensured);
        assertEquals(2, r.getExtracted());
        assertEquals(2, r.getPersisted());
        assertTrue(r.isSweepRan());
        assertFalse(r.isSweepSkipped());
        assertEquals(2, sink.sweptKeep.size());
    }

    @Test
    void emptySourceSweepIsSafetyGuarded() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeViolationImportService.Result r =
                new NativeViolationImportService(new FakePages(List.of("{\"rows\":[]}")), sink, 100, true).importAll();
        assertEquals(0, r.getExtracted());
        assertTrue(r.isSweepRan());
        assertTrue(r.isSweepSkipped());
    }
}
