package com.keyforge.iiq.application;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code application} migration table.
 * Explicit DDL inside the configured {@code PG_SCHEMA}; does NOT clone
 * {@code public.application}. Idempotent upsert keyed on {@code applicationid}.
 */
public class ApplicationRepository {

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

    public ApplicationRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ApplicationRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_application";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "applicationid uuid PRIMARY KEY, "
                        + "name text, "
                        + "type text, "
                        + "owner_id uuid, "
                        + "owner_display_name text, "
                        + "schemas jsonb, "
                        + "features jsonb, "
                        + "descriptions jsonb, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (applicationid, name, type, owner_id, owner_display_name, schemas, features, "
                        + "descriptions, created_at, modified_at) "
                        + "VALUES (?::uuid, ?, ?, ?::uuid, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?) "
                        + "ON CONFLICT (applicationid) DO UPDATE SET "
                        + "name = EXCLUDED.name, type = EXCLUDED.type, owner_id = EXCLUDED.owner_id, "
                        + "owner_display_name = EXCLUDED.owner_display_name, schemas = EXCLUDED.schemas, "
                        + "features = EXCLUDED.features, descriptions = EXCLUDED.descriptions, "
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

    public UpsertOutcome upsert(Connection conn, ApplicationRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.applicationid());
            ps.setString(2, row.name());
            ps.setString(3, row.type());
            ps.setString(4, row.ownerId());
            ps.setString(5, row.ownerDisplayName());
            ps.setString(6, row.schemasJson());
            ps.setString(7, row.featuresJson());
            ps.setString(8, row.descriptionsJson());
            setTimestamp(ps, 9, row.createdAt());
            setTimestamp(ps, 10, row.modifiedAt());

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
            ps.setObject(index, value.atOffset(java.time.ZoneOffset.UTC));
        }
    }
}
