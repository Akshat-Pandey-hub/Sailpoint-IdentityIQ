package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/**
 * JDBC-backed {@link NativeLinkSink}: writes to {@code iiq_native.kf_account} via
 * {@link NativeLinkRepository} and runs the current-state deletion sweep via the shared
 * {@link SoftDeleteSweeper}. The only class in the Link import path that touches SQL.
 */
public final class JdbcNativeLinkSink implements NativeLinkSink {

    private final Connection conn;
    private final NativeLinkRepository repository;
    private final SoftDeleteSweeper sweeper;

    public JdbcNativeLinkSink(Connection conn, NativeLinkRepository repository, String schema) {
        this.conn = conn;
        this.repository = repository;
        this.sweeper = new SoftDeleteSweeper(schema);
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeLinkRepository.UpsertOutcome upsert(NativeLinkRecord record) throws SQLException {
        return repository.upsert(conn, record);
    }

    @Override
    public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepAccountIds) throws SQLException {
        return sweeper.sweep(conn, "kf_account", "accountid", keepAccountIds);
    }
}
