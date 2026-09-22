package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native Role (Bundle) rows — separates orchestration from JDBC so the
 * orchestration is unit-testable with a fake sink. Production impl: {@link JdbcNativeRoleSink}.
 */
public interface NativeRoleSink {

    void ensure() throws SQLException;

    NativeRoleRepository.UpsertOutcome upsert(NativeRoleRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepRoleIds) throws SQLException;
}
