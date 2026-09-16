package com.keyforge.iiq.deletion;

/**
 * Pure SQL builders for the additive soft-delete mechanism (PDF CSS "deletion key sweep + soft
 * delete"). Table/column identifiers come from the fixed {@link DeletionDomain} catalog (never caller
 * input) and the schema is validated upstream, so they are inlined; the only bound parameter is the
 * authoritative source-id array. Nothing is ever hard-deleted.
 */
public final class SoftDeleteSql {

    private SoftDeleteSql() {
    }

    public static String addIsDeleted(String schema, String table) {
        return "ALTER TABLE " + schema + "." + table
                + " ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false";
    }

    public static String addDeletedAt(String schema, String table) {
        return "ALTER TABLE " + schema + "." + table + " ADD COLUMN IF NOT EXISTS deleted_at timestamptz";
    }

    /** Soft-delete rows whose PK is NOT in the current authoritative source set. */
    public static String markDeletedNotIn(String schema, String table, String pk) {
        return "UPDATE " + schema + "." + table + " SET is_deleted = true, deleted_at = now() "
                + "WHERE is_deleted = false AND NOT (" + pk + " = ANY(?))";
    }

    /** Resurrect rows that are present in the source again (previously soft-deleted). */
    public static String resurrectIn(String schema, String table, String pk) {
        return "UPDATE " + schema + "." + table + " SET is_deleted = false, deleted_at = NULL "
                + "WHERE is_deleted = true AND " + pk + " = ANY(?)";
    }

    public static String activeCount(String schema, String table) {
        return "SELECT count(*) FROM " + schema + "." + table + " WHERE is_deleted IS NOT TRUE";
    }

    public static String deletedCount(String schema, String table) {
        return "SELECT count(*) FROM " + schema + "." + table + " WHERE is_deleted = true";
    }
}
