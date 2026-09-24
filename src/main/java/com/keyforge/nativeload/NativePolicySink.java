package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native Policy rows. Production impl: {@link JdbcNativePolicySink}. */
public interface NativePolicySink {

    void ensure() throws SQLException;

    NativePolicyRepository.UpsertOutcome upsert(NativePolicyRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepPolicyIds) throws SQLException;
}
