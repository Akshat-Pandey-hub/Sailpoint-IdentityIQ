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
 * Plain-JDBC persistence for native TaskResult rows into {@code <schema>.kf_task_result}. Native-shaped:
 * rich scalar columns + {@code jsonb} for messages/statistics attributes.
 *
 * <p>Idempotent: PK {@code taskresultid} is the deterministic canonical UUID of the TaskResult id; every
 * write is {@code INSERT … ON CONFLICT DO UPDATE}. Each row carries a deterministic {@code record_hash}
 * over its business fields for change detection.
 */
public final class NativeTaskResultRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeTaskResultRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_task_result";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "taskresultid uuid PRIMARY KEY, "
                        + "source_id text, "
                        + "name text, "
                        + "type text, "
                        + "completion_status text, "
                        + "definition_name text, "
                        + "launcher text, "
                        + "host text, "
                        + "target_name text, "
                        + "target_class text, "
                        + "target_id text, "
                        + "schedule text, "
                        + "progress text, "
                        + "percent_complete integer, "
                        + "run_length integer, "
                        + "pending_signoffs integer, "
                        + "partitioned boolean, "
                        + "terminate_requested boolean, "
                        + "complete boolean, "
                        + "launched_at timestamptz, "
                        + "completed_at timestamptz, "
                        + "expiration_at timestamptz, "
                        + "verified_at timestamptz, "
                        + "messages jsonb, "
                        + "attributes jsonb, "
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
                        + "taskresultid, source_id, name, type, completion_status, definition_name, launcher, host, "
                        + "target_name, target_class, target_id, schedule, progress, percent_complete, run_length, "
                        + "pending_signoffs, partitioned, terminate_requested, complete, launched_at, completed_at, "
                        + "expiration_at, verified_at, messages, attributes, created_at, modified_at, record_hash, "
                        + "source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (taskresultid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, type = EXCLUDED.type, "
                        + "completion_status = EXCLUDED.completion_status, definition_name = EXCLUDED.definition_name, "
                        + "launcher = EXCLUDED.launcher, host = EXCLUDED.host, target_name = EXCLUDED.target_name, "
                        + "target_class = EXCLUDED.target_class, target_id = EXCLUDED.target_id, "
                        + "schedule = EXCLUDED.schedule, progress = EXCLUDED.progress, "
                        + "percent_complete = EXCLUDED.percent_complete, run_length = EXCLUDED.run_length, "
                        + "pending_signoffs = EXCLUDED.pending_signoffs, partitioned = EXCLUDED.partitioned, "
                        + "terminate_requested = EXCLUDED.terminate_requested, complete = EXCLUDED.complete, "
                        + "launched_at = EXCLUDED.launched_at, completed_at = EXCLUDED.completed_at, "
                        + "expiration_at = EXCLUDED.expiration_at, verified_at = EXCLUDED.verified_at, "
                        + "messages = EXCLUDED.messages, attributes = EXCLUDED.attributes, "
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
    public static String canonicalTaskResultId(NativeTaskResultRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            id = ParquetIds.deterministicUuid("native-task-result|" + (r.sourceId == null ? r.name : r.sourceId));
        }
        return id;
    }

    /** Deterministic SHA-256 over the business fields (excludes lineage + extracted_at). */
    public static String recordHash(NativeTaskResultRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("type", r.type);
        b.put("completion_status", r.completionStatus);
        b.put("definition_name", r.definitionName);
        b.put("launcher", r.launcher);
        b.put("host", r.host);
        b.put("target_name", r.targetName);
        b.put("target_class", r.targetClass);
        b.put("target_id", r.targetId);
        b.put("schedule", r.schedule);
        b.put("progress", r.progress);
        b.put("percent_complete", r.percentComplete);
        b.put("run_length", r.runLength);
        b.put("pending_signoffs", r.pendingSignoffs);
        b.put("partitioned", r.partitioned);
        b.put("terminate_requested", r.terminateRequested);
        b.put("complete", r.complete);
        b.put("launched_at", r.launched);
        b.put("completed_at", r.completed);
        b.put("expiration_at", r.expiration);
        b.put("verified_at", r.verified);
        b.put("messages", r.messagesJson);
        b.put("attributes", r.attributesJson);
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

    public UpsertOutcome upsert(Connection conn, NativeTaskResultRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalTaskResultId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.type);
            ps.setString(i++, r.completionStatus);
            ps.setString(i++, r.definitionName);
            ps.setString(i++, r.launcher);
            ps.setString(i++, r.host);
            ps.setString(i++, r.targetName);
            ps.setString(i++, r.targetClass);
            ps.setString(i++, r.targetId);
            ps.setString(i++, r.schedule);
            ps.setString(i++, r.progress);
            setInt(ps, i++, r.percentComplete);
            setInt(ps, i++, r.runLength);
            setInt(ps, i++, r.pendingSignoffs);
            setBool(ps, i++, r.partitioned);
            setBool(ps, i++, r.terminateRequested);
            setBool(ps, i++, r.complete);
            setTs(ps, i++, r.launched);
            setTs(ps, i++, r.completed);
            setTs(ps, i++, r.expiration);
            setTs(ps, i++, r.verified);
            ps.setString(i++, r.messagesJson);
            ps.setString(i++, r.attributesJson);
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

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
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
