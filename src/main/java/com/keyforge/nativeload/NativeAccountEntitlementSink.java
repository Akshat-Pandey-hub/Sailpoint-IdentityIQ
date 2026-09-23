package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native account-entitlement edges — separates orchestration from JDBC so the
 * orchestration is unit-testable with a fake sink. Production impl: {@link JdbcNativeAccountEntitlementSink}.
 */
public interface NativeAccountEntitlementSink {

    void ensure() throws SQLException;

    NativeAccountEntitlementRepository.UpsertOutcome upsert(NativeAccountEntitlementRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException;
}
