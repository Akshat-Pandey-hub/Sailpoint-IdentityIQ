package com.keyforge.iiq.workgroup;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code kf_workgroup} migration table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on {@code workgroupid}. Same
 * conventions as the other repositories (own DDL, {@code ON CONFLICT ... DO UPDATE},
 * {@code RETURNING (xmax = 0)}). Separate from the existing {@code usergroup} table, which
 * is untouched.
 */
public class WorkgroupRepository {

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

    public WorkgroupRepository() {
        this(DEFAULT_SCHEMA);
    }

    public WorkgroupRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_workgroup";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "workgroupid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "description text, "
                        + "owner_id uuid, "
                        + "owner_display_name text, "
                        + "status text, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "member_count integer, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (workgroupid, source_id, name, description, owner_id, owner_display_name, "
                        + "status, created_at, modified_at, member_count) "
                        + "VALUES (?::uuid, ?, ?, ?, ?::uuid, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (workgroupid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, "
                        + "description = EXCLUDED.description, owner_id = EXCLUDED.owner_id, "
                        + "owner_display_name = EXCLUDED.owner_display_name, status = EXCLUDED.status, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
                        + "member_count = EXCLUDED.member_count, extracted_at = now() "
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

    public UpsertOutcome upsert(Connection conn, WorkgroupRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.workgroupid());
            ps.setString(2, row.sourceId());
            ps.setString(3, row.name());
            ps.setString(4, row.description());
            ps.setString(5, row.ownerId());
            ps.setString(6, row.ownerDisplayName());
            ps.setString(7, row.status());
            setTimestamp(ps, 8, row.createdAt());
            setTimestamp(ps, 9, row.modifiedAt());
            setInt(ps, 10, row.memberCount());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, value);
        }
    }
}
