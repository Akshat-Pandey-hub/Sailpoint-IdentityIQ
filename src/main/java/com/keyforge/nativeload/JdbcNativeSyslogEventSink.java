package com.keyforge.nativeload;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC-backed {@link NativeSyslogEventSink}: appends to {@code iiq_native.kf_syslog_event}. Append-only —
 * no deletion sweep. Only class in the SyslogEvent import path that touches SQL.
 */
public final class JdbcNativeSyslogEventSink implements NativeSyslogEventSink {

    private final Connection conn;
    private final NativeSyslogEventRepository repository;

    public JdbcNativeSyslogEventSink(Connection conn, NativeSyslogEventRepository repository) {
        this.conn = conn;
        this.repository = repository;
    }

    @Override
    public void ensure() throws SQLException {
        repository.ensureTargetTable(conn);
    }

    @Override
    public NativeSyslogEventRepository.AppendOutcome append(NativeSyslogEventRecord record) throws SQLException {
        return repository.append(conn, record);
    }
}
