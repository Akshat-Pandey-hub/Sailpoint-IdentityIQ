package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration tests: dual-table upsert, count guard, parent-failure isolation, and soft-delete sweep. */
class NativeProvisioningTxnImportServiceTest {

    private static final class FakePages implements NativeProvisioningTxnPageSource {
        private final String page;
        FakePages(String page) { this.page = page; }
        @Override public String fetchPage(int start, int limit) { return start == 0 ? page : "{\"rows\":[]}"; }
    }

    private static final class FakeSink implements NativeProvisioningTxnSink {
        int itemUpserts;
        boolean failTxn;
        Collection<String> sweptTxns;
        Collection<String> sweptItems;
        @Override public void ensure() { }
        @Override public NativeProvisioningTxnRepository.UpsertOutcome upsertTxn(NativeProvisioningTxnRecord r) {
            if (failTxn) throw new IllegalStateException("secret-like-detail-must-not-be-returned");
            return NativeProvisioningTxnRepository.UpsertOutcome.INSERTED;
        }
        @Override public NativeProvisioningItemRepository.UpsertOutcome upsertItem(NativeProvisioningItemRecord r) {
            itemUpserts++;
            return NativeProvisioningItemRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweepTxns(Collection<String> keep) {
            this.sweptTxns = keep;
            return new SoftDeleteSweeper.SweepResult("kf_provisioning_txn", keep.size(), 0, 0, false, null);
        }
        @Override public SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keep) {
            this.sweptItems = keep;
            return new SoftDeleteSweeper.SweepResult("kf_provisioning_item", keep.size(), 0, 0, false, null);
        }
    }

    private static final String TXN_WITH_2_ITEMS =
            "{\"sourceCount\":1,\"rows\":[{\"sourceId\":\"t1\",\"items\":["
            + "{\"itemType\":\"ATTRIBUTE\",\"name\":\"memberOf\",\"value\":\"g1\",\"itemIndex\":0},"
            + "{\"itemType\":\"ATTRIBUTE\",\"name\":\"memberOf\",\"value\":\"g2\",\"itemIndex\":1}]}]}";

    @Test
    void persistsTxnAndItemsThenSoftSweepsBoth() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeProvisioningTxnImportService.Result r =
                new NativeProvisioningTxnImportService(new FakePages(TXN_WITH_2_ITEMS), sink, 100, true).importAll();
        assertEquals(1, r.getTxns());
        assertEquals(2, r.getItems());
        assertEquals(2, sink.itemUpserts);
        assertTrue(r.isSweepRan());
        assertEquals(1, sink.sweptTxns.size());
        assertEquals(2, sink.sweptItems.size(), "both items are in the item keep-set");
    }

    @Test
    void incompleteTxnScanThrowsBeforeSweeping() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeProvisioningTxnImportService(
                new FakePages("{\"sourceCount\":9,\"rows\":[{\"sourceId\":\"t1\",\"items\":[]}]}"),
                sink, 100, true).importAll());
        assertNull(sink.sweptTxns, "sweep must not run on an incomplete scan");
        assertNull(sink.sweptItems);
    }

    @Test
    void failedParentDoesNotPersistOrSweepItsChildren() throws SQLException {
        FakeSink sink = new FakeSink();
        sink.failTxn = true;
        NativeProvisioningTxnImportService.Result r = new NativeProvisioningTxnImportService(
                new FakePages(TXN_WITH_2_ITEMS), sink, 100, true).importAll();
        assertEquals(1, r.getFailed());
        assertEquals(0, sink.itemUpserts, "items are skipped when their parent transaction failed");
        assertTrue(r.getFailures().stream().noneMatch(s -> s.contains("secret-like-detail")),
                "failure messages carry only the exception class name, never source detail");
    }

    @Test
    void sweepDisabledDoesNotSweep() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeProvisioningTxnImportService.Result r =
                new NativeProvisioningTxnImportService(new FakePages(TXN_WITH_2_ITEMS), sink, 100, false).importAll();
        assertEquals(2, sink.itemUpserts);
        assertTrue(!r.isSweepRan());
        assertNull(sink.sweptTxns);
    }
}
