package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native Link (account) rows — separates orchestration from JDBC so the
 * orchestration is unit-testable with a fake sink. Production impl: {@link JdbcNativeLinkSink}.
 */
public interface NativeLinkSink {

    void ensure() throws SQLException;

    NativeLinkRepository.UpsertOutcome upsert(NativeLinkRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepAccountIds) throws SQLException;
}
