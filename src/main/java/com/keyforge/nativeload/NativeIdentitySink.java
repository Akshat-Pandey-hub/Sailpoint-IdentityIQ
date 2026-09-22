package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.Collection;

/**
 * Persistence seam for native Identity rows. Separates the extraction orchestration
 * ({@link NativeIdentityImportService}) from JDBC so the orchestration can be unit-tested with a fake
 * sink and no database. The production implementation is {@link JdbcNativeIdentitySink} (writes to
 * {@code iiq_native.kf_identity}); tests supply an in-memory fake.
 */
public interface NativeIdentitySink {

    /** Ensure the target schema/table (and soft-delete columns) exist. */
    void ensure() throws SQLException;

    /** Idempotent upsert of one record; returns whether the row was inserted or updated. */
    NativeIdentityRepository.UpsertOutcome upsert(NativeIdentityRecord record) throws SQLException;

    /**
     * Soft-delete sweep after a confirmed full scan: mark rows whose {@code userid} is not in the
     * authoritative keep-set as deleted, revive any that reappeared. The implementation must refuse an
     * empty keep-set (safety guard) so a failed/partial scan can never delete everything.
     */
    SoftDeleteSweeper.SweepResult sweep(Collection<String> keepUserids) throws SQLException;
}
