package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration tests for {@link NativeAccountEntitlementImportService} (Link-based pagination + guard). */
class NativeAccountEntitlementImportServiceTest {

    private static final class FakePages implements NativeAccountEntitlementPageSource {
        private final List<String> pages;
        final List<Integer> starts = new ArrayList<>();
        FakePages(List<String> pages) { this.pages = pages; }
        @Override public String fetchPage(int start, int limit) {
            starts.add(start);
            int idx = limit > 0 ? start / limit : 0;
            return idx < pages.size() ? pages.get(idx) : "{\"returnedLinks\":0,\"rows\":[]}";
        }
    }

    private static final class FakeSink implements NativeAccountEntitlementSink {
        Collection<String> sweptKeep;
        @Override public void ensure() { }
        @Override public NativeAccountEntitlementRepository.UpsertOutcome upsert(NativeAccountEntitlementRecord r) {
            return NativeAccountEntitlementRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweep(Collection<String> keep) {
            this.sweptKeep = keep;
            return new SoftDeleteSweeper.SweepResult("kf_account_entitlement", keep.size(), 0, 0, false, null);
        }
    }

    private static String edge(String link, String attr, String val) {
        return "{\"linkId\":\"" + link + "\",\"attributeName\":\"" + attr + "\",\"attributeValue\":\"" + val + "\"}";
    }

    @Test
    void oneLinkManyEdgesSweepsWhenLinkScanMatches() throws SQLException {
        FakePages src = new FakePages(List.of("{\"sourceCount\":1,\"returnedLinks\":1,\"rows\":["
                + edge("l1", "memberOf", "g1") + "," + edge("l1", "memberOf", "g2") + "]}"));
        FakeSink sink = new FakeSink();
        NativeAccountEntitlementImportService.Result r =
                new NativeAccountEntitlementImportService(src, sink, 100, true).importAll();

        assertEquals(2, r.getExtracted(), "two edges from one Link");
        assertEquals(1, r.getScannedLinks());
        assertEquals(1, r.getSourceLinkCount());
        assertTrue(r.isSweepRan());
        assertEquals(2, sink.sweptKeep.size());
    }

    @Test
    void pagesByLinkNotByEdges() throws SQLException {
        // pageSize 2 Links: page0 returns 2 Links (== pageSize ⇒ continue), page1 returns 1 Link (⇒ stop).
        FakePages src = new FakePages(List.of(
                "{\"sourceCount\":3,\"returnedLinks\":2,\"rows\":[" + edge("l1", "a", "x") + "," + edge("l2", "a", "y") + "]}",
                "{\"sourceCount\":3,\"returnedLinks\":1,\"rows\":[" + edge("l3", "a", "z") + "]}"));
        FakeSink sink = new FakeSink();
        NativeAccountEntitlementImportService.Result r =
                new NativeAccountEntitlementImportService(src, sink, 2, true).importAll();

        assertEquals(3, r.getExtracted());
        assertEquals(3, r.getScannedLinks());
        assertTrue(r.isSweepRan());
        assertEquals(List.of(0, 2), src.starts, "must advance the window by Link page size");
    }

    @Test
    void incompleteLinkScanThrowsBeforeSweeping() {
        FakePages src = new FakePages(List.of("{\"sourceCount\":3,\"returnedLinks\":1,\"rows\":["
                + edge("l1", "a", "x") + "]}"));
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class,
                () -> new NativeAccountEntitlementImportService(src, sink, 2, true).importAll());
        assertNull(sink.sweptKeep, "sweep must not run when not all Links were scanned");
    }
}
