package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeWorkgroupSink}: writes to {@code iiq_native.kf_workgroup} via
 * {@link NativeWorkgroupRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the Workgroup import path that touches SQL.
 */
public final class JdbcNativeWorkgroupSink implements NativeWorkgroupSink {

    private final Connection conn;
    private final NativeWorkgroupRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeWorkgroupSink(Connection conn, NativeWorkgroupRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeWorkgroupRepository.UpsertOutcome upsert(NativeWorkgroupRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepWorkgroupIds) throws SQLException {
        return sweeper.sweep(conn, "kf_workgroup", "workgroupid", keepWorkgroupIds);
    }
}
