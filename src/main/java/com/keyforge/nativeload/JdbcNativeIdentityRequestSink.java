package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeIdentityRequestSink}: writes to {@code kf_identity_request},
 * {@code kf_identity_request_item} and {@code kf_identity_request_approval} via the three repositories and
 * runs all three current-state deletion sweeps via the shared {@link SoftDeleteSweeper} (soft-delete only —
 * rows are marked, never hard-removed).
 */
public final class JdbcNativeIdentityRequestSink implements NativeIdentityRequestSink {

    private final Connection conn;
    private final NativeIdentityRequestRepository requestRepository;
    private final NativeIdentityRequestItemRepository itemRepository;
    private final NativeIdentityRequestApprovalRepository approvalRepository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeIdentityRequestSink(Connection conn, NativeIdentityRequestRepository requestRepository,
                                         NativeIdentityRequestItemRepository itemRepository,
                                         NativeIdentityRequestApprovalRepository approvalRepository, String schema) {
        this.conn = conn;
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.approvalRepository = approvalRepository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        requestRepository.ensureTargetTable(conn);
        itemRepository.ensureTargetTable(conn);
        approvalRepository.ensureTargetTable(conn);
    }

    @Override
    public NativeIdentityRequestRepository.UpsertOutcome upsertRequest(NativeIdentityRequestRecord record)
            throws SQLException {
        return requestRepository.upsert(conn, record);
    }

    @Override
    public NativeIdentityRequestItemRepository.UpsertOutcome upsertItem(NativeIdentityRequestItemRecord record)
            throws SQLException {
        return itemRepository.upsert(conn, record);
    }

    @Override
    public NativeIdentityRequestApprovalRepository.UpsertOutcome upsertApproval(
            NativeIdentityRequestApprovalRecord record) throws SQLException {
        return approvalRepository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweepRequests(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_identity_request", "identityrequestid", keepIds);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_identity_request_item", "identityrequestitemid", keepIds);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweepApprovals(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_identity_request_approval", "identityrequestapprovalid", keepIds);
    }
}
