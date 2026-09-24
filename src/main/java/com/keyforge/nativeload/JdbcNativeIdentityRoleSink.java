package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/** JDBC-backed {@link NativeIdentityRoleSink}: writes to kf_identity_role and runs the soft-delete sweep. */
public final class JdbcNativeIdentityRoleSink implements NativeIdentityRoleSink {

    private final Connection conn;
    private final NativeIdentityRoleRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeIdentityRoleSink(Connection conn, NativeIdentityRoleRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeIdentityRoleRepository.UpsertOutcome upsert(NativeIdentityRoleRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_identity_role", "identityroleid", keepIds);
    }
}
