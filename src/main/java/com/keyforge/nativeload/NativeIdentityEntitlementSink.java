package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native IdentityEntitlement rows — separates orchestration from JDBC so the
 * orchestration is unit-testable with a fake sink. Production impl:
 * {@link JdbcNativeIdentityEntitlementSink}.
 */
public interface NativeIdentityEntitlementSink {

    void ensure() throws SQLException;

    NativeIdentityEntitlementRepository.UpsertOutcome upsert(NativeIdentityEntitlementRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException;
}
