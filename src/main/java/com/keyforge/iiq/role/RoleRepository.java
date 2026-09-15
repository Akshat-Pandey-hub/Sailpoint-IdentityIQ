package com.keyforge.iiq.role;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code kf_role} migration table. Explicit
 * DDL inside {@code PG_SCHEMA}; idempotent upsert on {@code roleid}. Follows the same
 * conventions as {@link com.keyforge.iiq.entitlement.EntitlementRepository} (own DDL,
 * {@code ON CONFLICT ... DO UPDATE}, {@code RETURNING (xmax = 0)} to distinguish insert
 * from update).
 */
public class RoleRepository {

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

    public RoleRepository() {
        this(DEFAULT_SCHEMA);
    }

    public RoleRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_role";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "roleid uuid PRIMARY KEY, "
                        + "name text, "
                        + "displayable_name text, "
                        + "role_type text, "
                        + "role_type_display text, "
                        + "enabled boolean, "
                        + "owner_id uuid, "
                        + "owner_display_name text, "
                        + "descriptions jsonb, "
                        + "classifications jsonb, "
                        + "activation_date timestamptz, "
                        + "deactivation_date timestamptz, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (roleid, name, displayable_name, role_type, role_type_display, enabled, "
                        + "owner_id, owner_display_name, descriptions, classifications, "
                        + "activation_date, deactivation_date, created_at, modified_at) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?::uuid, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?) "
                        + "ON CONFLICT (roleid) DO UPDATE SET "
                        + "name = EXCLUDED.name, displayable_name = EXCLUDED.displayable_name, "
                        + "role_type = EXCLUDED.role_type, role_type_display = EXCLUDED.role_type_display, "
                        + "enabled = EXCLUDED.enabled, owner_id = EXCLUDED.owner_id, "
                        + "owner_display_name = EXCLUDED.owner_display_name, "
                        + "descriptions = EXCLUDED.descriptions, classifications = EXCLUDED.classifications, "
                        + "activation_date = EXCLUDED.activation_date, "
                        + "deactivation_date = EXCLUDED.deactivation_date, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
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

    public UpsertOutcome upsert(Connection conn, RoleRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.roleid());
            ps.setString(2, row.name());
            ps.setString(3, row.displayableName());
            ps.setString(4, row.roleType());
            ps.setString(5, row.roleTypeDisplay());
            setBool(ps, 6, row.enabled());
            ps.setString(7, row.ownerId());
            ps.setString(8, row.ownerDisplayName());
            ps.setString(9, row.descriptionsJson());
            ps.setString(10, row.classificationsJson());
            setTimestamp(ps, 11, row.activationDate());
            setTimestamp(ps, 12, row.deactivationDate());
            setTimestamp(ps, 13, row.createdAt());
            setTimestamp(ps, 14, row.modifiedAt());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
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
