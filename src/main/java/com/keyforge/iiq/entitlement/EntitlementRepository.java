package com.keyforge.iiq.entitlement;

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
 * Plain-JDBC data access for the project-owned {@code entitlement} migration table.
 * Explicit DDL inside {@code PG_SCHEMA}; does NOT clone {@code public.entitlement} and
 * uses no ISPM enum for {@code type} (stored as plain text). Idempotent upsert on
 * {@code entitlementid}.
 */
public class EntitlementRepository {

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

    public EntitlementRepository() {
        this(DEFAULT_SCHEMA);
    }

    public EntitlementRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "entitlementid uuid PRIMARY KEY, "
                        + "value text, "
                        + "displayable_name text, "
                        + "type text, "
                        + "attribute text, "
                        + "aggregated boolean, "
                        + "requestable boolean, "
                        + "instanceid uuid, "
                        + "application_display_name text, "
                        + "last_refresh timestamptz, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "schemas jsonb, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (entitlementid, value, displayable_name, type, attribute, aggregated, requestable, "
                        + "instanceid, application_display_name, last_refresh, created_at, modified_at, schemas) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid, ?, ?, ?, ?, ?::jsonb) "
                        + "ON CONFLICT (entitlementid) DO UPDATE SET "
                        + "value = EXCLUDED.value, displayable_name = EXCLUDED.displayable_name, "
                        + "type = EXCLUDED.type, attribute = EXCLUDED.attribute, aggregated = EXCLUDED.aggregated, "
                        + "requestable = EXCLUDED.requestable, instanceid = EXCLUDED.instanceid, "
                        + "application_display_name = EXCLUDED.application_display_name, "
                        + "last_refresh = EXCLUDED.last_refresh, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, schemas = EXCLUDED.schemas, extracted_at = now() "
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

    /** Canonical instanceids present in {@code <schema>.applicationinstance} (empty if absent). */
    public Set<String> getExistingInstanceIds(Connection conn) throws SQLException {
        Set<String> ids = new HashSet<>();
        String instanceTable = schema + ".applicationinstance";
        if (!tableExists(conn, instanceTable)) {
            return ids;
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT instanceid::text FROM " + instanceTable)) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null) {
                    ids.add(v);
                }
            }
        }
        return ids;
    }

    public UpsertOutcome upsert(Connection conn, EntitlementRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.entitlementid());
            ps.setString(2, row.value());
            ps.setString(3, row.displayableName());
            ps.setString(4, row.type());
            ps.setString(5, row.attribute());
            setBool(ps, 6, row.aggregated());
            setBool(ps, 7, row.requestable());
            ps.setString(8, row.instanceid());
            ps.setString(9, row.applicationDisplayName());
            setTimestamp(ps, 10, row.lastRefresh());
            setTimestamp(ps, 11, row.createdAt());
            setTimestamp(ps, 12, row.modifiedAt());
            ps.setString(13, row.schemasJson());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private boolean tableExists(Connection conn, String qualifiedTable) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?)")) {
            ps.setString(1, qualifiedTable);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null;
            }
        }
    }

    private static void setBool(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
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
