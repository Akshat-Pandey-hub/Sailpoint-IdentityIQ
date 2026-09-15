package com.keyforge.iiq.objectowner;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC data access for the project-owned {@code kf_object_owner} edge table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on the deterministic
 * {@code ownerid}. Same conventions as the other repositories.
 */
public class ObjectOwnerRepository {

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

    public ObjectOwnerRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ObjectOwnerRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_object_owner";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "ownerid uuid PRIMARY KEY, "
                        + "object_type text, "
                        + "object_id uuid, "
                        + "object_name text, "
                        + "ownership_role text, "
                        + "owner_id uuid, "
                        + "owner_display_name text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (ownerid, object_type, object_id, object_name, ownership_role, "
                        + "owner_id, owner_display_name) "
                        + "VALUES (?::uuid, ?, ?::uuid, ?, ?, ?::uuid, ?) "
                        + "ON CONFLICT (ownerid) DO UPDATE SET "
                        + "object_type = EXCLUDED.object_type, object_id = EXCLUDED.object_id, "
                        + "object_name = EXCLUDED.object_name, ownership_role = EXCLUDED.ownership_role, "
                        + "owner_id = EXCLUDED.owner_id, owner_display_name = EXCLUDED.owner_display_name, "
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

    public UpsertOutcome upsert(Connection conn, ObjectOwnerRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.ownerid());
            ps.setString(2, row.objectType());
            ps.setString(3, row.objectId());
            ps.setString(4, row.objectName());
            ps.setString(5, row.ownershipRole());
            ps.setString(6, row.ownerId());
            ps.setString(7, row.ownerDisplayName());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }
}
