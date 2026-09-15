package com.keyforge.iiq.workflow;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code kf_workflow_definition} table.
 * Explicit DDL inside {@code PG_SCHEMA}; idempotent upsert on {@code workflowid}.
 */
public class WorkflowRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public WorkflowRepository() {
        this(DEFAULT_SCHEMA);
    }

    public WorkflowRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_workflow_definition";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "workflowid uuid PRIMARY KEY, "
                        + "name text, "
                        + "type text, "
                        + "handler text, "
                        + "description text, "
                        + "approval_scheme text, "
                        + "approval_mode text, "
                        + "approval_levels text, "
                        + "escalation text, "
                        + "esignature text, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable
                        + " (workflowid, name, type, handler, description, approval_scheme, approval_mode, "
                        + "approval_levels, escalation, esignature, created_at, modified_at) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (workflowid) DO UPDATE SET "
                        + "name = EXCLUDED.name, type = EXCLUDED.type, handler = EXCLUDED.handler, "
                        + "description = EXCLUDED.description, approval_scheme = EXCLUDED.approval_scheme, "
                        + "approval_mode = EXCLUDED.approval_mode, approval_levels = EXCLUDED.approval_levels, "
                        + "escalation = EXCLUDED.escalation, esignature = EXCLUDED.esignature, "
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

    public UpsertOutcome upsert(Connection conn, WorkflowRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, row.workflowid());
            ps.setString(2, row.name());
            ps.setString(3, row.type());
            ps.setString(4, row.handler());
            ps.setString(5, row.description());
            ps.setString(6, row.approvalScheme());
            ps.setString(7, row.approvalMode());
            ps.setString(8, row.approvalLevels());
            ps.setString(9, row.escalation());
            ps.setString(10, row.esignature());
            setTimestamp(ps, 11, row.createdAt());
            setTimestamp(ps, 12, row.modifiedAt());
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
