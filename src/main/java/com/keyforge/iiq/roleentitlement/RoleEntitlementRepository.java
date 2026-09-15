package com.keyforge.iiq.roleentitlement;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC data access for the project-owned {@code kf_role_entitlement} edge table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on the deterministic {@code id}.
 * May legitimately hold zero rows when the instance's roles grant access via required roles
 * (hierarchy) rather than their own profiles.
 */
public class RoleEntitlementRepository {

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

    public RoleEntitlementRepository() {
        this(DEFAULT_SCHEMA);
    }

    public RoleEntitlementRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_role_entitlement";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "id uuid PRIMARY KEY, "
                        + "role_id uuid, "
                        + "role_name text, "
                        + "application_name text, "
                        + "property text, "
                        + "value text, "
                        + "display_value text, "
                        + "classifications text, "
                        + "entitlement_id uuid, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (id, role_id, role_name, application_name, property, value, "
                        + "display_value, classifications, entitlement_id) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?::uuid) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "role_id = EXCLUDED.role_id, role_name = EXCLUDED.role_name, "
                        + "application_name = EXCLUDED.application_name, property = EXCLUDED.property, "
                        + "value = EXCLUDED.value, display_value = EXCLUDED.display_value, "
                        + "classifications = EXCLUDED.classifications, entitlement_id = EXCLUDED.entitlement_id, "
                        + "extracted_at = now() "
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

    public UpsertOutcome upsert(Connection conn, RoleEntitlementRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.id());
            ps.setString(2, row.roleId());
            ps.setString(3, row.roleName());
            ps.setString(4, row.applicationName());
            ps.setString(5, row.property());
            ps.setString(6, row.value());
            ps.setString(7, row.displayValue());
            ps.setString(8, row.classifications());
            ps.setString(9, row.entitlementId());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }
}
