package com.keyforge.iiq.deletion;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reusable CSS deletion-detection / soft-delete sweep (PDF current-state-sync: "nightly deletion key
 * sweep + soft delete"). After a <b>full</b> extraction, a domain passes the authoritative current
 * source id set; rows in the table whose PK is no longer in that set are marked deleted
 * ({@code is_deleted=true, deleted_at=now()}) — never hard-deleted — and rows that reappear are
 * revived. Domain-agnostic: any CSS table with a UUID primary key can reuse it.
 *
 * <p><b>Safety:</b> an empty source set is refused (skipped), so a transient empty/failed fetch can
 * never mark an entire table deleted. Idempotent: re-running with the same source set marks/revives 0.
 */
public final class SoftDeleteSweeper {

    private final String schema;

    public SoftDeleteSweeper(String schema) {
        this.schema = SchemaName.validate(schema);
    }

    /** Outcome of one sweep. {@code skipped} = the safety guard fired (empty source set). */
    public record SweepResult(String table, int currentIdCount, int marked, int revived,
                              boolean skipped, String note) {
    }

    public static String addIsDeletedSql(String qualifiedTable) {
        return "ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS is_deleted boolean NOT NULL DEFAULT false";
    }

    public static String addDeletedAtSql(String qualifiedTable) {
        return "ALTER TABLE " + qualifiedTable + " ADD COLUMN IF NOT EXISTS deleted_at timestamptz";
    }

    public static String markSql(String qualifiedTable, String pkColumn) {
        return "UPDATE " + qualifiedTable + " SET is_deleted = true, deleted_at = now() "
                + "WHERE NOT (" + pkColumn + " = ANY(?)) AND is_deleted = false";
    }

    public static String reviveSql(String qualifiedTable, String pkColumn) {
        return "UPDATE " + qualifiedTable + " SET is_deleted = false, deleted_at = NULL "
                + "WHERE " + pkColumn + " = ANY(?) AND is_deleted = true";
    }

    /** De-duplicates and drops null/blank ids; an empty result triggers the safety skip. */
    static Set<String> normalizeIds(Collection<String> currentIds) {
        Set<String> ids = new LinkedHashSet<>();
        if (currentIds != null) {
            for (String id : currentIds) {
                if (id != null && !id.isBlank()) {
                    ids.add(id.trim());
                }
            }
        }
        return ids;
    }

    /** Adds the soft-delete columns if absent (additive; existing rows default to not-deleted). */
    public void ensureColumns(Connection conn, String table) throws SQLException {
        String t = schema + "." + table;
        try (Statement st = conn.createStatement()) {
            st.execute(addIsDeletedSql(t));
            st.execute(addDeletedAtSql(t));
        }
    }

    /**
     * Sweeps one table against the current source id set.
     *
     * @param table      unqualified table name
     * @param pkColumn   its UUID primary-key column
     * @param currentIds the authoritative current source ids (canonical UUID strings)
     */
    public SweepResult sweep(Connection conn, String table, String pkColumn, Collection<String> currentIds)
            throws SQLException {
        String t = schema + "." + table;
        ensureColumns(conn, table);

        Set<String> ids = normalizeIds(currentIds);
        if (ids.isEmpty()) {
            // Safety: never mark a whole table deleted from an empty/failed source fetch.
            return new SweepResult(table, 0, 0, 0, true, "empty source id set — sweep skipped (safety guard)");
        }

        Array idArray = conn.createArrayOf("uuid", ids.toArray());
        try {
            int marked;
            try (PreparedStatement ps = conn.prepareStatement(markSql(t, pkColumn))) {
                ps.setArray(1, idArray);
                marked = ps.executeUpdate();
            }
            int revived;
            try (PreparedStatement ps = conn.prepareStatement(reviveSql(t, pkColumn))) {
                ps.setArray(1, idArray);
                revived = ps.executeUpdate();
            }
            return new SweepResult(table, ids.size(), marked, revived, false, null);
        } finally {
            idArray.free();
        }
    }
}
