package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeTaskScheduleSink}: writes to {@code iiq_native.kf_task_schedule} via
 * {@link NativeTaskScheduleRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the TaskSchedule import path that touches SQL.
 */
public final class JdbcNativeTaskScheduleSink implements NativeTaskScheduleSink {

    private final Connection conn;
    private final NativeTaskScheduleRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeTaskScheduleSink(Connection conn, NativeTaskScheduleRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeTaskScheduleRepository.UpsertOutcome upsert(NativeTaskScheduleRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepTaskScheduleIds) throws SQLException {
        return sweeper.sweep(conn, "kf_task_schedule", "taskscheduleid", keepTaskScheduleIds);
    }
}
