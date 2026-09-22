package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native ManagedAttribute rows. Separates orchestration
 * ({@link NativeManagedAttributeImportService}) from JDBC so the orchestration is unit-testable with a
 * fake sink and no database. Production impl: {@link JdbcNativeManagedAttributeSink}.
 */
public interface NativeManagedAttributeSink {

    void ensure() throws SQLException;

    NativeManagedAttributeRepository.UpsertOutcome upsert(NativeManagedAttributeRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepEntitlementIds) throws SQLException;
}
