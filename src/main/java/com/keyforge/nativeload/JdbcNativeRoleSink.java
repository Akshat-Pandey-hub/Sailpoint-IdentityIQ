package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeRoleSink}: writes to {@code iiq_native.kf_role} via
 * {@link NativeRoleRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the Role import path that touches SQL.
 */
public final class JdbcNativeRoleSink implements NativeRoleSink {

    private final Connection conn;
    private final NativeRoleRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeRoleSink(Connection conn, NativeRoleRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeRoleRepository.UpsertOutcome upsert(NativeRoleRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepRoleIds) throws SQLException {
        return sweeper.sweep(conn, "kf_role", "roleid", keepRoleIds);
    }
}
