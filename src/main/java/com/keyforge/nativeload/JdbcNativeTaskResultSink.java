package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeTaskResultSink}: writes to {@code iiq_native.kf_task_result} via
 * {@link NativeTaskResultRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the TaskResult import path that touches SQL.
 */
public final class JdbcNativeTaskResultSink implements NativeTaskResultSink {

    private final Connection conn;
    private final NativeTaskResultRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeTaskResultSink(Connection conn, NativeTaskResultRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeTaskResultRepository.UpsertOutcome upsert(NativeTaskResultRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepTaskResultIds) throws SQLException {
        return sweeper.sweep(conn, "kf_task_result", "taskresultid", keepTaskResultIds);
    }
}
