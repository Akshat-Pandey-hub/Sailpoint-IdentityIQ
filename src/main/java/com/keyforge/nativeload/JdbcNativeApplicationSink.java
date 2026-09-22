package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeApplicationSink}: writes to {@code iiq_native.kf_application} via
 * {@link NativeApplicationRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the Application import path that touches SQL.
 */
public final class JdbcNativeApplicationSink implements NativeApplicationSink {

    private final Connection conn;
    private final NativeApplicationRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeApplicationSink(Connection conn, NativeApplicationRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeApplicationRepository.UpsertOutcome upsert(NativeApplicationRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepApplicationIds) throws SQLException {
        return sweeper.sweep(conn, "kf_application", "applicationid", keepApplicationIds);
    }
}
