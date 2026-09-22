package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeIdentitySink}: writes native Identity rows to {@code iiq_native.kf_identity}
 * via {@link NativeIdentityRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. This is the only class in the native-import path that touches SQL — the
 * extraction orchestration itself stays JDBC-free.
 */
public final class JdbcNativeIdentitySink implements NativeIdentitySink {

    private final Connection conn;
    private final NativeIdentityRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeIdentitySink(Connection conn, NativeIdentityRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeIdentityRepository.UpsertOutcome upsert(NativeIdentityRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepUserids) throws SQLException {
        return sweeper.sweep(conn, "kf_identity", "userid", keepUserids);
    }
}
