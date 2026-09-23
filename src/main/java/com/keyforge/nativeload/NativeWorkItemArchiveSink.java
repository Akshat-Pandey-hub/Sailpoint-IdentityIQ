package com.keyforge.nativeload;

import java.sql.SQLException;

/**
 * Persistence seam for native WorkItemArchive rows — separates orchestration from JDBC so the orchestration
 * is unit-testable with a fake sink. Append-only: there is no sweep method because archives are immutable
 * CEC/history records (nothing is ever expired or soft-deleted). Production impl:
 * {@link JdbcNativeWorkItemArchiveSink}.
 */
public interface NativeWorkItemArchiveSink {

    void ensure() throws SQLException;

    NativeWorkItemArchiveRepository.AppendOutcome append(NativeWorkItemArchiveRecord record) throws SQLException;
}
