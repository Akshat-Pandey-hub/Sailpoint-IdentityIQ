package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeGroupDefinitionSink}: writes to {@code iiq_native.kf_group_definition} via
 * {@link NativeGroupDefinitionRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the GroupDefinition import path that touches SQL.
 */
public final class JdbcNativeGroupDefinitionSink implements NativeGroupDefinitionSink {

    private final Connection conn;
    private final NativeGroupDefinitionRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeGroupDefinitionSink(Connection conn, NativeGroupDefinitionRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeGroupDefinitionRepository.UpsertOutcome upsert(NativeGroupDefinitionRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepGroupIds) throws SQLException {
        return sweeper.sweep(conn, "kf_group_definition", "groupid", keepGroupIds);
    }
}
