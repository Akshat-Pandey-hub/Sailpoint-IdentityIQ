package com.keyforge.iiq.assignment;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * Plain-JDBC data access for the project-owned {@code entitlementassignment} migration
 * table. Explicit DDL inside {@code PG_SCHEMA}; does NOT clone
 * {@code public.entitlementassignment}. Idempotent upsert on {@code assignmentid}.
 */
public class EntitlementAssignmentRepository {

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

    public EntitlementAssignmentRepository() {
        this(DEFAULT_SCHEMA);
    }

    public EntitlementAssignmentRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".entitlementassignment";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "assignmentid uuid PRIMARY KEY, "
                        + "accountid uuid, "
                        + "entitlementid uuid, "
                        + "account_native_name text, "
                        + "identity_id uuid, "
                        + "identity_display_name text, "
                        + "application_id uuid, "
                        + "application_name text, "
                        + "entitlement_value text, "
                        + "entitlement_type text, "
                        + "source_attribute text, "
                        + "resolution_status text, "
                        + "resolution_sources jsonb, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (assignmentid, accountid, entitlementid, account_native_name, identity_id, "
                        + "identity_display_name, application_id, application_name, entitlement_value, "
                        + "entitlement_type, source_attribute, resolution_status, resolution_sources) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?::uuid, ?, ?::uuid, ?, ?, ?, ?, ?, ?::jsonb) "
                        + "ON CONFLICT (assignmentid) DO UPDATE SET "
                        + "accountid = EXCLUDED.accountid, entitlementid = EXCLUDED.entitlementid, "
                        + "account_native_name = EXCLUDED.account_native_name, identity_id = EXCLUDED.identity_id, "
                        + "identity_display_name = EXCLUDED.identity_display_name, "
                        + "application_id = EXCLUDED.application_id, application_name = EXCLUDED.application_name, "
                        + "entitlement_value = EXCLUDED.entitlement_value, "
                        + "entitlement_type = EXCLUDED.entitlement_type, "
                        + "source_attribute = EXCLUDED.source_attribute, "
                        + "resolution_status = EXCLUDED.resolution_status, "
                        + "resolution_sources = EXCLUDED.resolution_sources, extracted_at = now() "
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

    /** Canonical accountids present in {@code <schema>.kf_account} (empty if absent). */
    public Set<String> getExistingAccountIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".kf_account", "accountid");
    }

    /** Canonical entitlementids present in {@code <schema>.kf_entitlement} (empty if absent). */
    public Set<String> getExistingEntitlementIds(Connection conn) throws SQLException {
        return readUuidColumn(conn, schema + ".kf_entitlement", "entitlementid");
    }

    public UpsertOutcome upsert(Connection conn, EntitlementAssignmentRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.assignmentid());
            ps.setString(2, row.accountid());
            ps.setString(3, row.entitlementid());
            ps.setString(4, row.accountNativeName());
            ps.setString(5, row.identityId());
            ps.setString(6, row.identityDisplayName());
            ps.setString(7, row.applicationId());
            ps.setString(8, row.applicationName());
            ps.setString(9, row.entitlementValue());
            ps.setString(10, row.entitlementType());
            ps.setString(11, row.sourceAttribute());
            ps.setString(12, row.resolutionStatus());
            ps.setString(13, row.resolutionSourcesJson());

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
}
