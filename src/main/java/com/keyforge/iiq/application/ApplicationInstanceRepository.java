package com.keyforge.iiq.application;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Plain-JDBC data access for the project-owned {@code applicationinstance} migration
 * table. Explicit DDL inside {@code PG_SCHEMA}; does NOT clone
 * {@code public.applicationinstance}. Idempotent upsert keyed on {@code instanceid}.
 */
public class ApplicationInstanceRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome {
        INSERTED,
        UPDATED
    }

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public ApplicationInstanceRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ApplicationInstanceRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".applicationinstance";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "instanceid uuid PRIMARY KEY, "
                        + "applicationid uuid, "
                        + "instancename text, "
                        + "app_type text, "
                        + "owner_id uuid, "
                        + "owner_display_name text, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (instanceid, applicationid, instancename, app_type, owner_id, owner_display_name, "
                        + "created_at, modified_at) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?::uuid, ?, ?, ?) "
                        + "ON CONFLICT (instanceid) DO UPDATE SET "
                        + "applicationid = EXCLUDED.applicationid, instancename = EXCLUDED.instancename, "
                        + "app_type = EXCLUDED.app_type, owner_id = EXCLUDED.owner_id, "
                        + "owner_display_name = EXCLUDED.owner_display_name, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, extracted_at = now() "
                        + "RETURNING (xmax = 0) AS inserted";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    /** Canonical applicationids present in {@code <schema>.kf_application} (empty if absent). */
    public Set<String> getExistingApplicationIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".kf_application", "applicationid");
    }

    /** Canonical userids present in {@code <schema>.kf_identity} (empty if the table is absent). */
    public Set<String> getExistingUserIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".kf_identity", "userid");
    }

    public UpsertOutcome upsert(Connection conn, ApplicationInstanceRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.instanceid());
            ps.setString(2, row.applicationid());
            ps.setString(3, row.instancename());
            ps.setString(4, row.appType());
            ps.setString(5, row.ownerId());
            ps.setString(6, row.ownerDisplayName());
            setTimestamp(ps, 7, row.createdAt());
            setTimestamp(ps, 8, row.modifiedAt());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private Set<String> readUuidColumn(Connection conn, String qualifiedTable, String column)
            throws SQLException {
        Set<String> ids = new HashSet<>();
        if (!tableExists(conn, qualifiedTable)) {
            return ids;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + "::text FROM " + qualifiedTable)) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null) {
                    ids.add(v);
                }
            }
        }
        return ids;
    }

    private boolean tableExists(Connection conn, String qualifiedTable) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?)")) {
            ps.setString(1, qualifiedTable);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null;
            }
        }
    }

    private static void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, value.atOffset(java.time.ZoneOffset.UTC));
        }
    }
}
