package com.keyforge.nativeload;

import java.sql.Connection;
import java.sql.SQLException;

/** JDBC-backed {@link NativeProvisioningTxnSink}; SQL persistence is delegated to the repositories. */
public final class JdbcNativeProvisioningTxnSink implements NativeProvisioningTxnSink {

    private final Connection conn;
    private final NativeProvisioningTxnRepository txnRepository;
    private final NativeProvisioningItemRepository itemRepository;

    public JdbcNativeProvisioningTxnSink(Connection conn, NativeProvisioningTxnRepository txnRepository,
                                         NativeProvisioningItemRepository itemRepository) {
        this.conn = conn;
        this.txnRepository = txnRepository;
        this.itemRepository = itemRepository;
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

}
