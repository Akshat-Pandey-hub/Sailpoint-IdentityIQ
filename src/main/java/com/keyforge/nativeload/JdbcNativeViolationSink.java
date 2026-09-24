package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeViolationSink}: writes to {@code iiq_native.kf_violation} and runs the
 * current-state deletion sweep via {@link SoftDeleteSweeper} (remediated/cleared violations leave the
 * active set). Only class in the violation import path that touches SQL.
 */
public final class JdbcNativeViolationSink implements NativeViolationSink {

    private final Connection conn;
    private final NativeViolationRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeViolationSink(Connection conn, NativeViolationRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeViolationRepository.UpsertOutcome upsert(NativeViolationRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepViolationIds) throws SQLException {
        return sweeper.sweep(conn, "kf_violation", "violationid", keepViolationIds);
    }
}
