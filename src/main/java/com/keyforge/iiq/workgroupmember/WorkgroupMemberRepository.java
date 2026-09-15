package com.keyforge.iiq.workgroupmember;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC data access for the project-owned {@code kf_workgroup_member} edge table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on the deterministic {@code id}.
 * Separate from {@code usergroup}/{@code kf_workgroup}, which are untouched.
 */
public class WorkgroupMemberRepository {

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

    public WorkgroupMemberRepository() {
        this(DEFAULT_SCHEMA);
    }

    public WorkgroupMemberRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_workgroup_member";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "id uuid PRIMARY KEY, "
                        + "workgroup_id uuid, "
                        + "identity_id uuid, "
                        + "member_name text, "
                        + "first_name text, "
                        + "last_name text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (id, workgroup_id, identity_id, member_name, first_name, last_name) "
                        + "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "workgroup_id = EXCLUDED.workgroup_id, identity_id = EXCLUDED.identity_id, "
                        + "member_name = EXCLUDED.member_name, first_name = EXCLUDED.first_name, "
                        + "last_name = EXCLUDED.last_name, extracted_at = now() "
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

    public UpsertOutcome upsert(Connection conn, WorkgroupMemberRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.id());
            ps.setString(2, row.workgroupId());
            ps.setString(3, row.identityId());
            ps.setString(4, row.memberName());
            ps.setString(5, row.firstName());
            ps.setString(6, row.lastName());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }
}
