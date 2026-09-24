package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Append-only orchestration: insert-vs-skip, no sweep, and the complete-scan guard. No IIQ, no DB. */
class NativeAuditEventImportServiceTest {

    private static final class FakePages implements NativeAuditEventPageSource {
        private final List<String> pages;
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"sourceCount\":0,\"rows\":[]}";
        }
    }

    /** Fake sink: first time an id is seen -> INSERTED, subsequently -> SKIPPED (append-only semantics). */
    private static final class FakeSink implements NativeAuditEventSink {
        final Set<String> seen = new HashSet<>();
        @Override public void ensure() { }
        @Override public NativeAuditEventRepository.AppendOutcome append(NativeAuditEventRecord r) {
            return seen.add(r.sourceId)
                    ? NativeAuditEventRepository.AppendOutcome.INSERTED
                    : NativeAuditEventRepository.AppendOutcome.SKIPPED;
        }
    }

    private static String page(int sourceCount, String... ids) {
        StringBuilder sb = new StringBuilder("{\"sourceCount\":" + sourceCount + ",\"rows\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"sourceId\":\"").append(ids[i]).append("\",\"action\":\"Login\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void appendsNewAndSkipsAlreadyPresentAcrossRuns() throws SQLException {
        FakeSink sink = new FakeSink();
        // run 1: 2 events, both new
        NativeAuditEventImportService.Result r1 = new NativeAuditEventImportService(
                new FakePages(List.of(page(2, "a", "b"))), sink, 100).importAll();
        assertEquals(2, r1.getInserted());
        assertEquals(0, r1.getSkipped());
        assertEquals(2, r1.getSourceCount());
        // run 2: same 2 -> both skipped (append-only idempotent, no duplicates)
        NativeAuditEventImportService.Result r2 = new NativeAuditEventImportService(
                new FakePages(List.of(page(2, "a", "b"))), sink, 100).importAll();
        assertEquals(0, r2.getInserted());
        assertEquals(2, r2.getSkipped());
    }

    @Test
    void incompleteScanThrows() {
        // sourceCount says 5 but only 1 row delivered -> guard fires (no partial "success")
        assertThrows(NativeImportException.class, () -> new NativeAuditEventImportService(
                new FakePages(List.of(page(5, "a"))), new FakeSink(), 100).importAll());
    }

    @Test
    void emptyLogIsValidZeroResult() throws SQLException {
        NativeAuditEventImportService.Result r = new NativeAuditEventImportService(
                new FakePages(List.of("{\"sourceCount\":0,\"rows\":[]}")), new FakeSink(), 100).importAll();
        assertEquals(0, r.getExtracted());
        assertEquals(0, r.getInserted());
        assertEquals(0, r.getSourceCount());
    }
}
