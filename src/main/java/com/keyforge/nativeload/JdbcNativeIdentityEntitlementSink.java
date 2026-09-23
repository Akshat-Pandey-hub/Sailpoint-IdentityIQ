package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeIdentityEntitlementSink}: writes to {@code iiq_native.kf_identity_entitlement}
 * via {@link NativeIdentityEntitlementRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the IdentityEntitlement import path that touches SQL.
 */
public final class JdbcNativeIdentityEntitlementSink implements NativeIdentityEntitlementSink {

    private final Connection conn;
    private final NativeIdentityEntitlementRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeIdentityEntitlementSink(Connection conn, NativeIdentityEntitlementRepository repository,
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
    public NativeIdentityEntitlementRepository.UpsertOutcome upsert(NativeIdentityEntitlementRecord record)
            throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_identity_entitlement", "entitlementid", keepIds);
    }
}
