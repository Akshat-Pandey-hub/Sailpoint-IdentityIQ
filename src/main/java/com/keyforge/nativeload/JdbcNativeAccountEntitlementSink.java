package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeAccountEntitlementSink}: writes to {@code iiq_native.kf_account_entitlement} via
 * {@link NativeAccountEntitlementRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the account-entitlement import path that touches SQL.
 */
public final class JdbcNativeAccountEntitlementSink implements NativeAccountEntitlementSink {

    private final Connection conn;
    private final NativeAccountEntitlementRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeAccountEntitlementSink(Connection conn, NativeAccountEntitlementRepository repository,
                                            String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeAccountEntitlementRepository.UpsertOutcome upsert(NativeAccountEntitlementRecord record)
            throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_account_entitlement", "accountentitlementid", keepIds);
    }
}
