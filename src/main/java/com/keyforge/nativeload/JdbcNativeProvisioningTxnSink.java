package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeProvisioningTxnSink}: writes to {@code kf_provisioning_txn} +
 * {@code kf_provisioning_item} via the two repositories and runs both current-state deletion sweeps via the
 * shared {@link SoftDeleteSweeper} (soft-delete only — rows are marked, never hard-removed).
 */
public final class JdbcNativeProvisioningTxnSink implements NativeProvisioningTxnSink {

    private final Connection conn;
    private final NativeProvisioningTxnRepository txnRepository;
    private final NativeProvisioningItemRepository itemRepository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeProvisioningTxnSink(Connection conn, NativeProvisioningTxnRepository txnRepository,
                                         NativeProvisioningItemRepository itemRepository, String schema) {
        this.conn = conn;
        this.txnRepository = txnRepository;
        this.itemRepository = itemRepository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        txnRepository.ensureTargetTable(conn);
        itemRepository.ensureTargetTable(conn);
    }

    @Override
    public NativeProvisioningTxnRepository.UpsertOutcome upsertTxn(NativeProvisioningTxnRecord record) throws SQLException {
        return txnRepository.upsert(conn, record);
    }

    @Override
    public NativeProvisioningItemRepository.UpsertOutcome upsertItem(NativeProvisioningItemRecord record) throws SQLException {
        return itemRepository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweepTxns(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_provisioning_txn", "provisioningtxnid", keepIds);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_provisioning_item", "provisioningitemid", keepIds);
    }
}
