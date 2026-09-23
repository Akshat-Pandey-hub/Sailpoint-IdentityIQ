package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration tests for dual-table upsert, count validation, and parent-failure isolation. */
class NativeProvisioningTxnImportServiceTest {

    private static final class FakePages implements NativeProvisioningTxnPageSource {
        private final String page;
        FakePages(String page) { this.page = page; }
        @Override public String fetchPage(int start, int limit) { return start == 0 ? page : "{\"rows\":[]}"; }
    }

    private static final class FakeSink implements NativeProvisioningTxnSink {
        int itemUpserts;
        boolean failTxn;
        @Override public void ensure() { }
        @Override public NativeProvisioningTxnRepository.UpsertOutcome upsertTxn(NativeProvisioningTxnRecord r) {
            if (failTxn) throw new IllegalStateException("secret-like-detail-must-not-be-returned");
            return NativeProvisioningTxnRepository.UpsertOutcome.INSERTED;
        }
        @Override public NativeProvisioningItemRepository.UpsertOutcome upsertItem(NativeProvisioningItemRecord r) {
            itemUpserts++;
            return NativeProvisioningItemRepository.UpsertOutcome.INSERTED;
        }
    }

    private static final String TXN_WITH_2_ITEMS =
            "{\"sourceCount\":1,\"rows\":[{\"sourceId\":\"t1\",\"items\":["
            + "{\"itemType\":\"ATTRIBUTE\",\"name\":\"memberOf\",\"value\":\"g1\",\"itemIndex\":0},"
            + "{\"itemType\":\"ATTRIBUTE\",\"name\":\"memberOf\",\"value\":\"g2\",\"itemIndex\":1}]}]}";

    @Test
    void persistsTxnAndItemsWithoutDeletionSweep() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeProvisioningTxnImportService.Result r =
                new NativeProvisioningTxnImportService(new FakePages(TXN_WITH_2_ITEMS), sink, 100).importAll();
        assertEquals(1, r.getTxns());
        assertEquals(2, r.getItems());
        assertEquals(2, sink.itemUpserts);
        assertTrue(!r.isSweepRan());
        assertEquals(0, r.getTxnMarkedDeleted());
        assertEquals(0, r.getItemMarkedDeleted());
    }

    @Test
    void incompleteTxnScanThrowsWithoutAnySweep() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeProvisioningTxnImportService(
                new FakePages("{\"sourceCount\":9,\"rows\":[{\"sourceId\":\"t1\",\"items\":[]}]}"),
                sink, 100).importAll());
    }

    @Test
    void failedParentDoesNotPersistOrSweepItsChildren() throws SQLException {
        FakeSink sink = new FakeSink();
        sink.failTxn = true;
        NativeProvisioningTxnImportService.Result r = new NativeProvisioningTxnImportService(
                new FakePages(TXN_WITH_2_ITEMS), sink, 100).importAll();
        assertEquals(1, r.getFailed());
        assertEquals(0, sink.itemUpserts, "items are skipped when their parent transaction failed");
        assertTrue(r.getFailures().stream().noneMatch(s -> s.contains("secret-like-detail")));
    }
}
