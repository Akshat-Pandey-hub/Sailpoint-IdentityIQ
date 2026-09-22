package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeManagedAttributeSink}: writes to {@code iiq_native.kf_entitlement} via
 * {@link NativeManagedAttributeRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the ManagedAttribute import path that touches SQL.
 */
public final class JdbcNativeManagedAttributeSink implements NativeManagedAttributeSink {

    private final Connection conn;
    private final NativeManagedAttributeRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeManagedAttributeSink(Connection conn, NativeManagedAttributeRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeManagedAttributeRepository.UpsertOutcome upsert(NativeManagedAttributeRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepEntitlementIds) throws SQLException {
        return sweeper.sweep(conn, "kf_entitlement", "entitlementid", keepEntitlementIds);
    }
}
