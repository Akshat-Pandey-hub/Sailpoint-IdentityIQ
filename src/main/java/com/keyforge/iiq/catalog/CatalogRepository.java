package com.keyforge.iiq.catalog;

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
 * Plain-JDBC data access for the project-owned {@code catalog} migration table (the
 * requestable entitlement catalog). Explicit DDL inside {@code PG_SCHEMA}; does NOT
 * clone {@code public.catalog} — none of the dozens of unrelated ISPM columns or ISPM
 * defaults are inherited. Idempotent upsert on the deterministic {@code catalogid}.
 */
public class CatalogRepository {

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

    public CatalogRepository() {
        this(DEFAULT_SCHEMA);
    }

    public CatalogRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".catalog";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "catalogid uuid PRIMARY KEY, "
                        + "name text, "
                        + "type text, "
                        + "entitlementid uuid, "
                        + "entitlement_name text, "
                        + "entitlement_type text, "
                        + "requestable boolean, "
                        + "application_name text, "
                        + "appinstanceid uuid, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "source_entitlements jsonb, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (catalogid, name, type, entitlementid, entitlement_name, entitlement_type, "
                        + "requestable, application_name, appinstanceid, created_at, modified_at, source_entitlements) "
                        + "VALUES (?::uuid, ?, ?, ?::uuid, ?, ?, ?, ?, ?::uuid, ?, ?, ?::jsonb) "
                        + "ON CONFLICT (catalogid) DO UPDATE SET "
                        + "name = EXCLUDED.name, type = EXCLUDED.type, entitlementid = EXCLUDED.entitlementid, "
                        + "entitlement_name = EXCLUDED.entitlement_name, "
                        + "entitlement_type = EXCLUDED.entitlement_type, requestable = EXCLUDED.requestable, "
                        + "application_name = EXCLUDED.application_name, appinstanceid = EXCLUDED.appinstanceid, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
                        + "source_entitlements = EXCLUDED.source_entitlements, extracted_at = now() "
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

    public Set<String> getExistingEntitlementIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".kf_entitlement", "entitlementid");
    }

    public Set<String> getExistingInstanceIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".applicationinstance", "instanceid");
    }

    public UpsertOutcome upsert(Connection conn, CatalogRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.catalogid());
            ps.setString(2, row.name());
            ps.setString(3, row.type());
            ps.setString(4, row.entitlementid());
            ps.setString(5, row.entitlementName());
            ps.setString(6, row.entitlementType());
            setBool(ps, 7, row.requestable());
            ps.setString(8, row.applicationName());
            ps.setString(9, row.appinstanceid());
            setTimestamp(ps, 10, row.createdAt());
            setTimestamp(ps, 11, row.modifiedAt());
            ps.setString(12, row.sourceEntitlementsJson());

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
