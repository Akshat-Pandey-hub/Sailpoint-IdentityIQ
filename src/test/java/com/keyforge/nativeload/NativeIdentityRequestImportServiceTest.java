package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Orchestration: 3-table upsert, count guard, parent-failure isolation, and soft-delete sweep. */
class NativeIdentityRequestImportServiceTest {

    private static final class FakePages implements NativeIdentityRequestPageSource {
        private final String page;
        FakePages(String page) { this.page = page; }
        @Override public String fetchPage(int start, int limit) { return start == 0 ? page : "{\"rows\":[]}"; }
    }

    private static final class FakeSink implements NativeIdentityRequestSink {
        int itemUpserts;
        int approvalUpserts;
        boolean failRequest;
        Collection<String> sweptRequests;
        Collection<String> sweptItems;
        Collection<String> sweptApprovals;
        @Override public void ensure() { }
        @Override public NativeIdentityRequestRepository.UpsertOutcome upsertRequest(NativeIdentityRequestRecord r) {
            if (failRequest) throw new IllegalStateException("detail-must-not-leak");
            return NativeIdentityRequestRepository.UpsertOutcome.INSERTED;
        }
        @Override public NativeIdentityRequestItemRepository.UpsertOutcome upsertItem(NativeIdentityRequestItemRecord r) {
            itemUpserts++;
            return NativeIdentityRequestItemRepository.UpsertOutcome.INSERTED;
        }
        @Override public NativeIdentityRequestApprovalRepository.UpsertOutcome upsertApproval(NativeIdentityRequestApprovalRecord r) {
            approvalUpserts++;
            return NativeIdentityRequestApprovalRepository.UpsertOutcome.INSERTED;
        }
        @Override public SoftDeleteSweeper.SweepResult sweepRequests(Collection<String> keep) {
            this.sweptRequests = keep;
            return new SoftDeleteSweeper.SweepResult("kf_identity_request", keep.size(), 0, 0, false, null);
        }
        @Override public SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keep) {
            this.sweptItems = keep;
            return new SoftDeleteSweeper.SweepResult("kf_identity_request_item", keep.size(), 0, 0, false, null);
        }
        @Override public SoftDeleteSweeper.SweepResult sweepApprovals(Collection<String> keep) {
            this.sweptApprovals = keep;
            return new SoftDeleteSweeper.SweepResult("kf_identity_request_approval", keep.size(), 0, 0, false, null);
        }
    }

    // Real IdentityRequest ids are 32-hex (canonicalRequestId requires a UUID-shaped id).
    private static final String RID = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String REQ =
            "{\"sourceCount\":1,\"rows\":[{\"sourceId\":\"" + RID + "\","
            + "\"items\":[{\"sourceId\":\"ri1\",\"requestSourceId\":\"" + RID + "\"},"
            + "{\"sourceId\":\"ri2\",\"requestSourceId\":\"" + RID + "\"}],"
            + "\"approvals\":[{\"requestSourceId\":\"" + RID + "\",\"workItemId\":\"wi1\",\"owner\":\"m\",\"approvalIndex\":0}]}]}";

    @Test
    void persistsAllThreeThenSoftSweeps() throws SQLException {
        FakeSink sink = new FakeSink();
        NativeIdentityRequestImportService.Result r =
                new NativeIdentityRequestImportService(new FakePages(REQ), sink, 100, true).importAll();
        assertEquals(1, r.getRequests());
        assertEquals(2, r.getItems());
        assertEquals(1, r.getApprovals());
        assertEquals(2, sink.itemUpserts);
        assertEquals(1, sink.approvalUpserts);
        assertTrue(r.isSweepRan());
        assertEquals(1, sink.sweptRequests.size());
        assertEquals(2, sink.sweptItems.size());
        assertEquals(1, sink.sweptApprovals.size());
    }

    @Test
    void incompleteScanThrowsBeforeSweeping() {
        FakeSink sink = new FakeSink();
        assertThrows(NativeImportException.class, () -> new NativeIdentityRequestImportService(
                new FakePages("{\"sourceCount\":9,\"rows\":[{\"sourceId\":\"" + RID + "\"}]}"), sink, 100, true).importAll());
        assertNull(sink.sweptRequests);
    }

    @Test
    void failedParentSkipsChildrenAndHidesDetail() throws SQLException {
        FakeSink sink = new FakeSink();
        sink.failRequest = true;
        NativeIdentityRequestImportService.Result r =
                new NativeIdentityRequestImportService(new FakePages(REQ), sink, 100, true).importAll();
        assertEquals(1, r.getFailed());
        assertEquals(0, sink.itemUpserts, "items skipped when parent request failed");
        assertEquals(0, sink.approvalUpserts, "approvals skipped when parent request failed");
        assertTrue(r.getFailures().stream().noneMatch(s -> s.contains("detail-must-not-leak")));
    }
}
