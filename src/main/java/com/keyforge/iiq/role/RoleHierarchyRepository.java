package com.keyforge.iiq.role;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC data access for the project-owned {@code kf_role_hierarchy} edge table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on the deterministic
 * {@code hierarchyid}. May legitimately hold zero rows when the instance's roles carry
 * no inheritance/requirements/permits.
 */
public class RoleHierarchyRepository {

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

    public RoleHierarchyRepository() {
        this(DEFAULT_SCHEMA);
    }

    public RoleHierarchyRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_role_hierarchy";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "hierarchyid uuid PRIMARY KEY, "
                        + "role_id uuid, "
                        + "role_name text, "
                        + "related_role_id uuid, "
                        + "related_role_display_name text, "
                        + "edge_type text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (hierarchyid, role_id, role_name, related_role_id, related_role_display_name, edge_type) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?) "
                        + "ON CONFLICT (hierarchyid) DO UPDATE SET "
                        + "role_id = EXCLUDED.role_id, role_name = EXCLUDED.role_name, "
                        + "related_role_id = EXCLUDED.related_role_id, "
                        + "related_role_display_name = EXCLUDED.related_role_display_name, "
                        + "edge_type = EXCLUDED.edge_type, extracted_at = now() "
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

    public UpsertOutcome upsert(Connection conn, RoleHierarchyRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.hierarchyid());
            ps.setString(2, row.roleId());
            ps.setString(3, row.roleName());
            ps.setString(4, row.relatedRoleId());
            ps.setString(5, row.relatedRoleDisplayName());
            ps.setString(6, row.edgeType());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }
}
