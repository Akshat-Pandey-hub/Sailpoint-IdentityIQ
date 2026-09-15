package com.keyforge.iiq.taskresult;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the {@code kf_task_result} table. Explicit DDL inside {@code PG_SCHEMA};
 * idempotent upsert on {@code taskresultid}. {@code launched}/{@code completed} are {@code timestamptz}
 * (SCIM supplies real UTC instants); {@code messages} is {@code jsonb} (raw source array preserved).
 */
public class TaskResultRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String targetTable;

    public TaskResultRepository() {
        this(DEFAULT_SCHEMA);
    }

    public TaskResultRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_task_result";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                    + "taskresultid uuid PRIMARY KEY, "
                    + "source_id text, "
                    + "name text, "
                    + "type text, "
                    + "task_definition text, "
                    + "completion_status text, "
                    + "host text, "
                    + "launcher text, "
                    + "launched timestamptz, "
                    + "completed timestamptz, "
                    + "partitioned boolean, "
                    + "terminated boolean, "
                    + "pending_signoffs integer, "
                    + "messages jsonb, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public UpsertOutcome upsert(Connection conn, TaskResultRow r) throws SQLException {
        String sql = "INSERT INTO " + targetTable + " (taskresultid, source_id, name, type, task_definition, "
                + "completion_status, host, launcher, launched, completed, partitioned, terminated, "
                + "pending_signoffs, messages) "
                + "VALUES (?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) "
                + "ON CONFLICT (taskresultid) DO UPDATE SET source_id=EXCLUDED.source_id, name=EXCLUDED.name, "
                + "type=EXCLUDED.type, task_definition=EXCLUDED.task_definition, "
                + "completion_status=EXCLUDED.completion_status, host=EXCLUDED.host, launcher=EXCLUDED.launcher, "
                + "launched=EXCLUDED.launched, completed=EXCLUDED.completed, partitioned=EXCLUDED.partitioned, "
                + "terminated=EXCLUDED.terminated, pending_signoffs=EXCLUDED.pending_signoffs, "
                + "messages=EXCLUDED.messages, extracted_at=now() RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, r.taskresultid());
            ps.setString(i++, r.sourceId());
            ps.setString(i++, r.name());
            ps.setString(i++, r.type());
            ps.setString(i++, r.taskDefinition());
            ps.setString(i++, r.completionStatus());
            ps.setString(i++, r.host());
            ps.setString(i++, r.launcher());
            setTs(ps, i++, r.launched());
            setTs(ps, i++, r.completed());
            setBool(ps, i++, r.partitioned());
            setBool(ps, i++, r.terminated());
            setInt(ps, i++, r.pendingSignoffs());
            ps.setString(i++, r.messagesJson());
            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setTs(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP); else ps.setObject(i, v.atOffset(java.time.ZoneOffset.UTC));
    }

    private static void setBool(PreparedStatement ps, int i, Boolean v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BOOLEAN); else ps.setBoolean(i, v);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }
}
