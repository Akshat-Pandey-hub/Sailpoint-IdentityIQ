package com.keyforge.iiq.usergroup;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for Service #8's own {@code usergroup} table. Idempotent
 * upsert keyed on the primary key {@code id}, so re-running never creates duplicates.
 *
 * <p>Unlike Services #1–#7 (which clone a pre-existing {@code public} table with
 * {@code LIKE ... INCLUDING ALL}), User Groups have no upstream template table, so
 * this service defines and creates its own schema + table with explicit DDL — using
 * the same "CREATE SCHEMA / CREATE TABLE IF NOT EXISTS on demand" initialization
 * pattern. It depends on no other migration tables. The schema is the configured
 * {@code PG_SCHEMA} (default {@value com.keyforge.iiq.config.SchemaName#DEFAULT}).
 */
public class UserGroupRepository {

    /** Default migration schema when none is configured. */
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

    public UserGroupRepository() {
        this(DEFAULT_SCHEMA);
    }

    public UserGroupRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".usergroup";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "id uuid PRIMARY KEY, "
                        + "source_type varchar(50) NOT NULL, "
                        + "source_id varchar(255), "
                        + "name varchar(1024), "
                        + "description text, "
                        + "owner varchar(1024), "
                        + "status varchar(255), "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "member_count integer, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (id, source_type, source_id, name, description, owner, status, "
                        + "created_at, modified_at, member_count) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "source_type = EXCLUDED.source_type, "
                        + "source_id = EXCLUDED.source_id, "
                        + "name = EXCLUDED.name, "
                        + "description = EXCLUDED.description, "
                        + "owner = EXCLUDED.owner, "
                        + "status = EXCLUDED.status, "
                        + "created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, "
                        + "member_count = EXCLUDED.member_count, "
                        + "extracted_at = now() "
                        + "RETURNING (xmax = 0) AS inserted";
    }

    /** The configured target schema. */
    public String schema() {
        return schema;
    }

    /** Fully-qualified target table, e.g. {@code migration_test.usergroup}. */
    public String targetTable() {
        return targetTable;
    }

    /** Creates the schema and table if absent. Idempotent and safe to run repeatedly. */
    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, UserGroupRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.id());
            ps.setString(2, row.sourceType());
            ps.setString(3, row.sourceId());
            ps.setString(4, row.name());
            ps.setString(5, row.description());
            ps.setString(6, row.owner());
            ps.setString(7, row.status());
            setTimestamp(ps, 8, row.createdAt());
            setTimestamp(ps, 9, row.modifiedAt());
            setInteger(ps, 10, row.memberCount());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setTimestamp(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
