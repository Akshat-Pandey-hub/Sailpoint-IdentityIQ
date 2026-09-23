package com.keyforge.nativeload;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC-backed {@link NativeWorkItemArchiveSink}: appends to {@code iiq_native.kf_workitem_archive} via
 * {@link NativeWorkItemArchiveRepository}. The only class in the WorkItemArchive import path that touches
 * SQL. Append-only — no deletion sweep (archives are immutable CEC/history records).
 */
public final class JdbcNativeWorkItemArchiveSink implements NativeWorkItemArchiveSink {

    private final Connection conn;
    private final NativeWorkItemArchiveRepository repository;

    public JdbcNativeWorkItemArchiveSink(Connection conn, NativeWorkItemArchiveRepository repository) {
        this.conn = conn;
        this.repository = repository;
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeWorkItemArchiveRepository.AppendOutcome append(NativeWorkItemArchiveRecord record) throws SQLException {
        return repository.append(conn, record);
    }
}
