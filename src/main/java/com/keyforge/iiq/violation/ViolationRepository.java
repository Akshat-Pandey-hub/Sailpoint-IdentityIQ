package com.keyforge.iiq.violation;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code kf_violation} table. Explicit DDL inside
 * {@code PG_SCHEMA}; idempotent upsert on {@code violationid}. May hold zero rows (valid empty).
 */
public class ViolationRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public ViolationRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ViolationRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_violation";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "violationid uuid PRIMARY KEY, "
                        + "policy_name text, "
                        + "constraint_name text, "
                        + "status text, "
                        + "description text, "
                        + "owner_id uuid, "
                        + "owner_display_name text, "
                        + "identity_id uuid, "
                        + "identity_display_name text, "
                        + "mitigator text, "
                        + "expiration_date timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (violationid, policy_name, constraint_name, status, description, "
                        + "owner_id, owner_display_name, identity_id, identity_display_name, mitigator, expiration_date) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?::uuid, ?, ?::uuid, ?, ?, ?) "
                        + "ON CONFLICT (violationid) DO UPDATE SET "
                        + "policy_name = EXCLUDED.policy_name, constraint_name = EXCLUDED.constraint_name, "
                        + "status = EXCLUDED.status, description = EXCLUDED.description, "
                        + "owner_id = EXCLUDED.owner_id, owner_display_name = EXCLUDED.owner_display_name, "
                        + "identity_id = EXCLUDED.identity_id, identity_display_name = EXCLUDED.identity_display_name, "
                        + "mitigator = EXCLUDED.mitigator, expiration_date = EXCLUDED.expiration_date, "
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

    public UpsertOutcome upsert(Connection conn, ViolationRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.violationid());
            ps.setString(2, row.policyName());
            ps.setString(3, row.constraintName());
            ps.setString(4, row.status());
            ps.setString(5, row.description());
            ps.setString(6, row.ownerId());
            ps.setString(7, row.ownerDisplayName());
            ps.setString(8, row.identityId());
            ps.setString(9, row.identityDisplayName());
            ps.setString(10, row.mitigator());
            setTimestamp(ps, 11, row.expirationDate());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setTimestamp(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.TIMESTAMP);
        } else {
            ps.setObject(i, v.atOffset(java.time.ZoneOffset.UTC));
        }
    }
}
