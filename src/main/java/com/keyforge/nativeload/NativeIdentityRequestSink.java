package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native IdentityRequest + nested items + approvals — separates orchestration from JDBC.
 * All three tables are current-state (upsert + soft-delete): an absent request/item/approval is marked
 * {@code is_deleted} (never hard-removed). Production impl: {@link JdbcNativeIdentityRequestSink}.
 */
public interface NativeIdentityRequestSink {

    void ensure() throws SQLException;

    NativeIdentityRequestRepository.UpsertOutcome upsertRequest(NativeIdentityRequestRecord record) throws SQLException;

    NativeIdentityRequestItemRepository.UpsertOutcome upsertItem(NativeIdentityRequestItemRecord record) throws SQLException;

    NativeIdentityRequestApprovalRepository.UpsertOutcome upsertApproval(NativeIdentityRequestApprovalRecord record)
            throws SQLException;

    SoftDeleteSweeper.SweepResult sweepRequests(Collection<String> keepIds) throws SQLException;

    SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keepIds) throws SQLException;

    SoftDeleteSweeper.SweepResult sweepApprovals(Collection<String> keepIds) throws SQLException;
}
