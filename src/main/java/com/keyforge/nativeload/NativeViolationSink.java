package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native PolicyViolation rows. Production impl: {@link JdbcNativeViolationSink}. */
public interface NativeViolationSink {

    void ensure() throws SQLException;

    NativeViolationRepository.UpsertOutcome upsert(NativeViolationRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepViolationIds) throws SQLException;
}
