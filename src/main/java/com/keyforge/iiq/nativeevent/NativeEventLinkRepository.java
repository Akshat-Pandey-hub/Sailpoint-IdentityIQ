package com.keyforge.iiq.nativeevent;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC, append-only persistence for the native {@code kf_event_link} table in the isolated
 * {@code iiq_native} schema. Writes are strictly {@code INSERT ... ON CONFLICT (link_id) DO NOTHING} —
 * no UPDATE, no DELETE — so re-derivation never duplicates or mutates. Every row is an explicit-id
 * relationship (see {@link NativeEventLinkRow}); nothing is inferred.
 */
public final class NativeEventLinkRepository {

    private final String schema;
    private final String table;

    public NativeEventLinkRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_event_link";
    }

    public String schema() {
        return schema;
    }

    public String table() {
        return table;
    }

    public static String createTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "link_id uuid PRIMARY KEY, "
                + "event_id uuid, "
                + "src_object_type text, "
                + "src_object_id text, "
                + "target_object_type text, "
                + "target_object_id text, "
                + "link_type text, "
                + "link_status text, "
                + "extraction_run_id text, "
                + "extracted_at timestamptz NOT NULL DEFAULT now())";
    }

    public static String appendSql(String table) {
        return "INSERT INTO " + table + " (link_id, event_id, src_object_type, src_object_id, "
                + "target_object_type, target_object_id, link_type, link_status, extraction_run_id) "
                + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (link_id) DO NOTHING RETURNING link_id";
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute(createTableSql(table));
        }
    }

    /** Appends one link. Returns true if inserted, false if the deterministic link_id already existed. */
    public boolean append(Connection conn, NativeEventLinkRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(appendSql(table))) {
            int i = 1;
            ps.setString(i++, row.linkId());
            ps.setString(i++, row.eventId());
            ps.setString(i++, row.srcObjectType());
            ps.setString(i++, row.srcObjectId());
            ps.setString(i++, row.targetObjectType());
            ps.setString(i++, row.targetObjectId());
            ps.setString(i++, row.linkType());
            ps.setString(i++, row.linkStatus());
            ps.setString(i, row.extractionRunId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
