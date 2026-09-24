package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/** JDBC-backed {@link NativeWorkItemSink}: writes to kf_workitem and runs the deletion sweep. */
public final class JdbcNativeWorkItemSink implements NativeWorkItemSink {

    private final Connection conn;
    private final NativeWorkItemRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeWorkItemSink(Connection conn, NativeWorkItemRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeWorkItemRepository.UpsertOutcome upsert(NativeWorkItemRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException {
        return sweeper.sweep(conn, "kf_workitem", "workitemid", keepIds);
    }
}
