package com.keyforge.nativeload;

import java.sql.SQLException;

/**
 * Persistence seam for native AuditEvent rows. Append-only: there is no sweep method because audit
 * events are immutable historical evidence (never expired or soft-deleted). Production impl:
 * {@link JdbcNativeAuditEventSink}.
 */
public interface NativeAuditEventSink {

    void ensure() throws SQLException;

    NativeAuditEventRepository.AppendOutcome append(NativeAuditEventRecord record) throws SQLException;
}
