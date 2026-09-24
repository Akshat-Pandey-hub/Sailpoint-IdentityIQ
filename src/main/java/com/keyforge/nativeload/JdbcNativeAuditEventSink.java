package com.keyforge.nativeload;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC-backed {@link NativeAuditEventSink}: appends to {@code iiq_native.kf_audit_event}. Append-only —
 * no deletion sweep. Only class in the AuditEvent import path that touches SQL.
 */
public final class JdbcNativeAuditEventSink implements NativeAuditEventSink {

    private final Connection conn;
    private final NativeAuditEventRepository repository;

    public JdbcNativeAuditEventSink(Connection conn, NativeAuditEventRepository repository) {
        this.conn = conn;
        this.repository = repository;
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeAuditEventRepository.AppendOutcome append(NativeAuditEventRecord record) throws SQLException {
        return repository.append(conn, record);
    }
}
