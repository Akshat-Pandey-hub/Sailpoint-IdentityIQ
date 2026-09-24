package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native policy-constraint rows. Production impl: {@link JdbcNativePolicyConstraintSink}. */
public interface NativePolicyConstraintSink {

    void ensure() throws SQLException;

    NativePolicyConstraintRepository.UpsertOutcome upsert(NativePolicyConstraintRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepConstraintIds) throws SQLException;
}
