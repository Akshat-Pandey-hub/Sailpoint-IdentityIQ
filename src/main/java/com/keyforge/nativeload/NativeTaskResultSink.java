package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native TaskResult rows — separates orchestration from JDBC so the orchestration
 * is unit-testable with a fake sink. Production impl: {@link JdbcNativeTaskResultSink}.
 */
public interface NativeTaskResultSink {

    void ensure() throws SQLException;

    NativeTaskResultRepository.UpsertOutcome upsert(NativeTaskResultRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepTaskResultIds) throws SQLException;
}
