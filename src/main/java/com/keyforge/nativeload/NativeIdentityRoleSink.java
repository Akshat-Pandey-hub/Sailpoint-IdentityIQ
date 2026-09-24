package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/** Persistence seam for native identity-role edges. Production impl: {@link JdbcNativeIdentityRoleSink}. */
public interface NativeIdentityRoleSink {

    void ensure() throws SQLException;

    NativeIdentityRoleRepository.UpsertOutcome upsert(NativeIdentityRoleRecord record) throws SQLException;

    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) throws SQLException;
}
