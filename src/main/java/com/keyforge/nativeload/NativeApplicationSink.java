package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native Application rows — separates orchestration from JDBC so the
 * orchestration is unit-testable with a fake sink. Production impl: {@link JdbcNativeApplicationSink}.
 */
public interface NativeApplicationSink {

    void ensure() throws SQLException;

    NativeApplicationRepository.UpsertOutcome upsert(NativeApplicationRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepApplicationIds) throws SQLException;
}
