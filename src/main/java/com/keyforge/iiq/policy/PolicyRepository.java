package com.keyforge.iiq.policy;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Plain-JDBC data access for the project-owned {@code kf_policy} table. Explicit DDL inside
 * {@code PG_SCHEMA}; idempotent upsert on {@code policyid}. May hold zero rows (valid empty).
 */
public class PolicyRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public PolicyRepository() {
        this(DEFAULT_SCHEMA);
    }

    public PolicyRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_policy";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "policyid uuid PRIMARY KEY, "
                        + "name text, "
                        + "type text, "
                        + "state text, "
                        + "description text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (policyid, name, type, state, description) "
                        + "VALUES (?::uuid, ?, ?, ?, ?) "
                        + "ON CONFLICT (policyid) DO UPDATE SET "
                        + "name = EXCLUDED.name, type = EXCLUDED.type, state = EXCLUDED.state, "
                        + "description = EXCLUDED.description, extracted_at = now() "
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

    public UpsertOutcome upsert(Connection conn, PolicyRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.policyid());
            ps.setString(2, row.name());
            ps.setString(3, row.type());
            ps.setString(4, row.state());
            ps.setString(5, row.description());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }
}
