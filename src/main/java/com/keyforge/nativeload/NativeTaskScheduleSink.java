package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native TaskSchedule rows — separates orchestration from JDBC so the
 * orchestration is unit-testable with a fake sink. Production impl: {@link JdbcNativeTaskScheduleSink}.
 */
public interface NativeTaskScheduleSink {

    void ensure() throws SQLException;

    NativeTaskScheduleRepository.UpsertOutcome upsert(NativeTaskScheduleRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepTaskScheduleIds) throws SQLException;
}
