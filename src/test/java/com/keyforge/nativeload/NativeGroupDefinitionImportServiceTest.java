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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration tests for {@link NativeGroupDefinitionImportService} using fakes — no IIQ, no DB. */
class NativeGroupDefinitionImportServiceTest {

    private static final String HEX1 = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String HEX2 = "1a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String HEX3 = "2a1b2c3d4e5f60718293a4b5c6d7e8f9";

    private static final class FakePages implements NativeGroupDefinitionPageSource {
        private final List<String> pages;
        final List<Integer> starts = new ArrayList<>();
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            starts.add(start);
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"rows\":[]}";
        }
    }

    private static final class FakeSink implements NativeGroupDefinitionSink {
        final Set<String> failOn = new HashSet<>();
        Collection<String> sweptKeep;
        boolean ensured;
        @Override public void ensure() { ensured = true; }
        @Override public NativeGroupDefinitionRepository.UpsertOutcome upsert(NativeGroupDefinitionRecord rec) throws SQLException {
            if (failOn.contains(rec.sourceId)) {
                throw new SQLException("boom " + rec.sourceId);
            }
            return NativeGroupDefinitionRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.sweptKeep = keep;
            if (keep.isEmpty()) {
                return new SoftDeleteSweeper.SweepResult("kf_group_definition", 0, 0, 0, true, "empty");
            }
            return new SoftDeleteSweeper.SweepResult("kf_group_definition", keep.size(), 0, 0, false, null);
        }
    }

    private static String row(String hex, String name, String type) {
        return "{\"sourceId\":\"" + hex + "\",\"name\":\"" + name + "\",\"type\":\"" + type + "\"}";
    }

    @Test
    void countsGroupsAndPopulationsSeparately() throws SQLException {
        FakePages src = new FakePages(List.of("{\"rows\":["
                + row(HEX1, "Dept-HR", "GROUP") + ","
                + row(HEX2, "Dept-IT", "GROUP") + ","
                + row(HEX3, "Contractors", "POPULATION") + "]}"));
        FakeSink sink = new FakeSink();
        NativeGroupDefinitionImportService.Result r =
                new NativeGroupDefinitionImportService(src, sink, 100, true).importAll();

        assertEquals(3, r.getExtracted());
        assertEquals(2, r.getGroups(), "GROUP count preserved");
        assertEquals(1, r.getPopulations(), "POPULATION count preserved");
        assertEquals(3, r.getPersisted());
        assertTrue(r.isSweepRan());
        assertFalse(r.isSweepSkipped());
        assertEquals(3, sink.sweptKeep.size());
    }

    @Test
    void isolatesRowFailuresButKeepsFailedIdInSweepSet() throws SQLException {
        FakePages src = new FakePages(List.of("{\"rows\":["
                + row(HEX1, "Dept-HR", "GROUP") + "," + row(HEX2, "Contractors", "POPULATION") + "]}"));
        FakeSink sink = new FakeSink();
        sink.failOn.add(HEX2);
        NativeGroupDefinitionImportService.Result r =
                new NativeGroupDefinitionImportService(src, sink, 100, true).importAll();

        assertEquals(2, r.getExtracted());
        assertEquals(1, r.getFailed());
        assertEquals(1, r.getPersisted());
        assertEquals(2, sink.sweptKeep.size());
    }

    @Test
    void emptySourceRunsSweepButSafetyGuardSkipsIt() throws SQLException {
        FakePages src = new FakePages(List.of("{\"rows\":[]}"));
        FakeSink sink = new FakeSink();
        NativeGroupDefinitionImportService.Result r =
                new NativeGroupDefinitionImportService(src, sink, 100, true).importAll();

        assertEquals(0, r.getExtracted());
        assertTrue(r.isSweepRan());
        assertTrue(r.isSweepSkipped());
    }

    @Test
    void sweepDisabledDoesNotSweep() throws SQLException {
        FakePages src = new FakePages(List.of("{\"rows\":[" + row(HEX1, "Dept-HR", "GROUP") + "]}"));
        FakeSink sink = new FakeSink();
        NativeGroupDefinitionImportService.Result r =
                new NativeGroupDefinitionImportService(src, sink, 100, false).importAll();

        assertEquals(1, r.getPersisted());
        assertFalse(r.isSweepRan());
        assertNull(sink.sweptKeep);
    }
}
