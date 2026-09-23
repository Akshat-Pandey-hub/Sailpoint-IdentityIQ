package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NativeWorkItemArchiveImportServiceTest {
    private static final class Pages implements NativeWorkItemArchivePageSource {
        private final List<String> values;
        Pages(List<String> values) { this.values = values; }
        @Override public String fetchPage(int start, int limit) {
            int index = start / limit;
            return index < values.size() ? values.get(index) : "{\"sourceCount\":1,\"rows\":[]}";
        }
    }

    private static final class Sink implements NativeWorkItemArchiveSink {
        int ensures;
        final Set<String> ids = new HashSet<>();
        @Override public void ensure() { ensures++; }
        @Override public NativeWorkItemArchiveRepository.AppendOutcome append(NativeWorkItemArchiveRecord r) {
            return ids.add(r.sourceId) ? NativeWorkItemArchiveRepository.AppendOutcome.INSERTED
                    : NativeWorkItemArchiveRepository.AppendOutcome.SKIPPED;
        }
    }

    @Test
    void completeScanAppendsAndRerunDeduplicatesWithoutDeletionSemantics() throws SQLException {
        String row = "{\"sourceId\":\"7f0001019f061fdc819fa50301271752\",\"archived\":\"2026-09-01T10:11:12Z\"}";
        String page = "{\"sourceCount\":1,\"rows\":[" + row + "]}";
        Sink sink = new Sink();
        NativeWorkItemArchiveImportService svc = new NativeWorkItemArchiveImportService(new Pages(List.of(page)), sink, 10);
        NativeWorkItemArchiveImportService.Result first = svc.importAll();
        NativeWorkItemArchiveImportService.Result second = svc.importAll();
        assertEquals(1, first.getSourceCount());
        assertEquals(1, first.getExtracted());
        assertEquals(1, first.getInserted());
        assertEquals(1, second.getSkipped());
        assertEquals(1, sink.ids.size());
        assertEquals(2, sink.ensures);
    }

    @Test
    void emptySourceIsValidAndNeverCallsAnySweep() throws SQLException {
        Sink sink = new Sink();
        NativeWorkItemArchiveImportService.Result r = new NativeWorkItemArchiveImportService(
                new Pages(List.of("{\"sourceCount\":0,\"rows\":[]}")), sink, 10).importAll();
        assertEquals(0, r.getSourceCount());
        assertEquals(0, r.getExtracted());
        assertEquals(0, r.getInserted());
        assertTrue(sink.ids.isEmpty());
    }

    @Test
    void incompleteScanFailsClearlyWithoutTreatingItAsDeletion() {
        String page = "{\"sourceCount\":2,\"rows\":[{\"sourceId\":\"a\"}]}";
        assertThrows(NativeImportException.class, () -> new NativeWorkItemArchiveImportService(
                new Pages(List.of(page)), new Sink(), 10).importAll());
    }
}
