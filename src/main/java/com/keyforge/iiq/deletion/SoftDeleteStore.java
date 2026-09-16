package com.keyforge.iiq.deletion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * The persistence operations the deletion sweep needs. Extracted as an interface so
 * {@link DeletionSweepService} can be unit-tested with a fake and without a database.
 */
public interface SoftDeleteStore {

    /** Adds {@code is_deleted}/{@code deleted_at} if absent (additive; existing rows default false). */
    void ensureColumns(Connection conn, String table) throws SQLException;

    /** Clears the deleted flag on rows present in the source again; returns rows resurrected. */
    int resurrectIn(Connection conn, String table, String pk, Set<String> sourceIds) throws SQLException;

    /** Soft-deletes rows whose PK is not in the source set; returns rows newly soft-deleted. */
    int markDeletedNotIn(Connection conn, String table, String pk, Set<String> sourceIds) throws SQLException;

    long activeCount(Connection conn, String table) throws SQLException;

    long deletedCount(Connection conn, String table) throws SQLException;
}
