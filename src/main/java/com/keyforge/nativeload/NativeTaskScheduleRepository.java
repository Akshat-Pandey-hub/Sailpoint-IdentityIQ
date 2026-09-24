package com.keyforge.nativeload;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.parquet.ParquetIds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-JDBC persistence for native TaskSchedule rows into {@code <schema>.kf_task_schedule}.
 * Native-shaped: scalar columns + {@code jsonb} for cron expressions/arguments.
 *
 * <p>Idempotent: PK {@code taskscheduleid} is the deterministic canonical UUID of the TaskSchedule id;
 * every write is {@code INSERT … ON CONFLICT DO UPDATE}. Each row carries a deterministic
 * {@code record_hash} over its business fields for change detection.
 */
public final class NativeTaskScheduleRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeTaskScheduleRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_task_schedule";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "taskscheduleid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "description text, "
                        + "definition_name text, "
                        + "state text, "
                        + "new_state text, "
                        + "launcher text, "
                        + "host text, "
                        + "last_launch_error text, "
                        + "delete_on_finish boolean, "
                        + "last_execution_at timestamptz, "
                        + "next_execution_at timestamptz, "
                        + "next_actual_execution_at timestamptz, "
                        + "resume_at timestamptz, "
                        + "cron_expressions jsonb, "
                        + "arguments jsonb, "
                        + "created_at timestamptz, "
                        + "modified_at timestamptz, "
                        + "record_hash text, "
                        + "source_system text, "
                        + "source_interface text, "
                        + "source_object_type text, "
                        + "extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "taskscheduleid, source_id, name, description, definition_name, state, new_state, launcher, "
                        + "host, last_launch_error, delete_on_finish, last_execution_at, next_execution_at, "
                        + "next_actual_execution_at, resume_at, cron_expressions, arguments, created_at, modified_at, "
                        + "record_hash, source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (taskscheduleid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, description = EXCLUDED.description, "
                        + "definition_name = EXCLUDED.definition_name, state = EXCLUDED.state, "
                        + "new_state = EXCLUDED.new_state, launcher = EXCLUDED.launcher, host = EXCLUDED.host, "
                        + "last_launch_error = EXCLUDED.last_launch_error, delete_on_finish = EXCLUDED.delete_on_finish, "
                        + "last_execution_at = EXCLUDED.last_execution_at, next_execution_at = EXCLUDED.next_execution_at, "
                        + "next_actual_execution_at = EXCLUDED.next_actual_execution_at, resume_at = EXCLUDED.resume_at, "
                        + "cron_expressions = EXCLUDED.cron_expressions, arguments = EXCLUDED.arguments, "
                        + "created_at = EXCLUDED.created_at, modified_at = EXCLUDED.modified_at, "
                        + "record_hash = EXCLUDED.record_hash, source_system = EXCLUDED.source_system, "
                        + "source_interface = EXCLUDED.source_interface, source_object_type = EXCLUDED.source_object_type, "
                        + "extraction_run_id = EXCLUDED.extraction_run_id, extracted_at = now() "
                        + "RETURNING (xmax = 0) AS inserted";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    /** Deterministic PK: canonical UUID of the source id, with a stable name-based fallback. Never random. */
    public static String canonicalTaskScheduleId(NativeTaskScheduleRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-task-schedule|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeTaskScheduleRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("description", r.description);
        b.put("definition_name", r.definitionName);
        b.put("state", r.state);
        b.put("new_state", r.newState);
        b.put("launcher", r.launcher);
        b.put("host", r.host);
        b.put("last_launch_error", r.lastLaunchError);
        b.put("delete_on_finish", r.deleteOnFinish);
        b.put("last_execution_at", r.lastExecution);
        b.put("next_execution_at", r.nextExecution);
        b.put("next_actual_execution_at", r.nextActualExecution);
        b.put("resume_at", r.resumeDate);
        b.put("cron_expressions", r.cronExpressionsJson);
        b.put("arguments", r.argumentsJson);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeTaskScheduleRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalTaskScheduleId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.description);
            ps.setString(i++, r.definitionName);
            ps.setString(i++, r.state);
            ps.setString(i++, r.newState);
            ps.setString(i++, r.launcher);
            ps.setString(i++, r.host);
            ps.setString(i++, r.lastLaunchError);
            setBool(ps, i++, r.deleteOnFinish);
            setTs(ps, i++, r.lastExecution);
            setTs(ps, i++, r.nextExecution);
            setTs(ps, i++, r.nextActualExecution);
            setTs(ps, i++, r.resumeDate);
            ps.setString(i++, r.cronExpressionsJson);
            ps.setString(i++, r.argumentsJson);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            ps.setString(i++, recordHash(r));
            ps.setString(i++, r.srcSystem);
            ps.setString(i++, r.srcInterface);
            ps.setString(i++, r.srcObjectType);
            ps.setString(i, r.extractionRunId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setBool(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private static void setTs(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, value.atOffset(ZoneOffset.UTC));
        }
    }
}
