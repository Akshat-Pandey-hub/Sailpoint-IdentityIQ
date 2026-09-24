package com.keyforge.nativeload;

import java.sql.SQLException;

/**
 * Persistence seam for native SyslogEvent rows. Append-only: no sweep method (syslog events are immutable
 * operational log records). Production impl: {@link JdbcNativeSyslogEventSink}.
 */
public interface NativeSyslogEventSink {

    void ensure() throws SQLException;

    NativeSyslogEventRepository.AppendOutcome append(NativeSyslogEventRecord record) throws SQLException;
}
