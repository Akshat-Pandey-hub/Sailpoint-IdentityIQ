package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native ProvisioningTransaction + derived items — separates orchestration from JDBC.
 * Both tables are current-state (upsert + soft-delete), per the locked design: a transaction is mutable
 * until completion and IIQ prunes old ones, so an absent transaction is marked {@code is_deleted} (never
 * hard-removed — history is preserved). Production impl: {@link JdbcNativeProvisioningTxnSink}.
 */
public interface NativeProvisioningTxnSink {

    void ensure() throws SQLException;

    NativeProvisioningTxnRepository.UpsertOutcome upsertTxn(NativeProvisioningTxnRecord record) throws SQLException;

    NativeProvisioningItemRepository.UpsertOutcome upsertItem(NativeProvisioningItemRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweepTxns(Collection<String> keepIds) throws SQLException;

    SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keepIds) throws SQLException;
}
