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
 * Plain-JDBC persistence for native IdentityRequest rows into {@code <schema>.kf_identity_request}
 * (current-state: upsert + soft-delete sweep). Business-content {@code record_hash} captures the mutable
 * state/status so an update fires only when the source actually changed.
 */
public final class NativeIdentityRequestRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeIdentityRequestRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_identity_request";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "identityrequestid uuid PRIMARY KEY, source_id text, name text, type text, "
                        + "user_friendly_type text, state text, source text, source_object text, "
                        + "completion_status text, execution_status text, priority text, requester_id text, "
                        + "requester_display_name text, target_id text, target_display_name text, "
                        + "external_ticket_id text, process_id text, task_result_id text, executing boolean, "
                        + "failure boolean, rejected boolean, successful boolean, terminated boolean, "
                        + "incomplete boolean, iiq_only boolean, provisioning_complete boolean, "
                        + "end_date timestamptz, verified timestamptz, created_at timestamptz, "
                        + "modified_at timestamptz, owner_id text, owner_name text, errors jsonb, "
                        + "item_count integer, approval_count integer, record_hash text, source_system text, "
                        + "source_interface text, source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "identityrequestid, source_id, name, type, user_friendly_type, state, source, "
                        + "source_object, completion_status, execution_status, priority, requester_id, "
                        + "requester_display_name, target_id, target_display_name, external_ticket_id, "
                        + "process_id, task_result_id, executing, failure, rejected, successful, terminated, "
                        + "incomplete, iiq_only, provisioning_complete, end_date, verified, created_at, "
                        + "modified_at, owner_id, owner_name, errors, item_count, approval_count, record_hash, "
                        + "source_system, source_interface, source_object_type, extraction_run_id) "
                        + "VALUES (?::uuid, " + String.join(", ", java.util.Collections.nCopies(31, "?"))
                        + ", ?::jsonb, " + String.join(", ", java.util.Collections.nCopies(7, "?")) + ") "
                        + "ON CONFLICT (identityrequestid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, type = EXCLUDED.type, "
                        + "user_friendly_type = EXCLUDED.user_friendly_type, state = EXCLUDED.state, "
                        + "source = EXCLUDED.source, source_object = EXCLUDED.source_object, "
                        + "completion_status = EXCLUDED.completion_status, "
                        + "execution_status = EXCLUDED.execution_status, priority = EXCLUDED.priority, "
                        + "requester_id = EXCLUDED.requester_id, "
                        + "requester_display_name = EXCLUDED.requester_display_name, "
                        + "target_id = EXCLUDED.target_id, target_display_name = EXCLUDED.target_display_name, "
                        + "external_ticket_id = EXCLUDED.external_ticket_id, process_id = EXCLUDED.process_id, "
                        + "task_result_id = EXCLUDED.task_result_id, executing = EXCLUDED.executing, "
                        + "failure = EXCLUDED.failure, rejected = EXCLUDED.rejected, "
                        + "successful = EXCLUDED.successful, terminated = EXCLUDED.terminated, "
                        + "incomplete = EXCLUDED.incomplete, iiq_only = EXCLUDED.iiq_only, "
                        + "provisioning_complete = EXCLUDED.provisioning_complete, end_date = EXCLUDED.end_date, "
                        + "verified = EXCLUDED.verified, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, errors = EXCLUDED.errors, "
                        + "item_count = EXCLUDED.item_count, approval_count = EXCLUDED.approval_count, "
                        + "record_hash = EXCLUDED.record_hash, source_system = EXCLUDED.source_system, "
                        + "source_interface = EXCLUDED.source_interface, "
                        + "source_object_type = EXCLUDED.source_object_type, "
                        + "extraction_run_id = EXCLUDED.extraction_run_id, extracted_at = now() "
                        + "RETURNING (xmax = 0) AS inserted";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    String upsertSql() {
        return upsertSql;
    }

    public static String canonicalRequestId(NativeIdentityRequestRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            throw new IllegalArgumentException("IdentityRequest source id is required for deterministic identity");
        }
        return id;
    }

    public static String recordHash(NativeIdentityRequestRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("type", r.type);
        b.put("user_friendly_type", r.userFriendlyType);
        b.put("state", r.state);
        b.put("source", r.source);
        b.put("source_object", r.sourceObject);
        b.put("completion_status", r.completionStatus);
        b.put("execution_status", r.executionStatus);
        b.put("priority", r.priority);
        b.put("requester_id", r.requesterId);
        b.put("requester_display_name", r.requesterDisplayName);
        b.put("target_id", r.targetId);
        b.put("target_display_name", r.targetDisplayName);
        b.put("external_ticket_id", r.externalTicketId);
        b.put("process_id", r.processId);
        b.put("task_result_id", r.taskResultId);
        b.put("executing", r.executing);
        b.put("failure", r.failure);
        b.put("rejected", r.rejected);
        b.put("successful", r.successful);
        b.put("terminated", r.terminated);
        b.put("incomplete", r.incomplete);
        b.put("iiq_only", r.iiqOnly);
        b.put("provisioning_complete", r.provisioningComplete);
        b.put("end_date", r.endDate);
        b.put("verified", r.verified);
        b.put("created_at", r.created);
        b.put("modified_at", r.modified);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("errors", r.errorsJson);
        b.put("item_count", r.itemCount);
        b.put("approval_count", r.approvalCount);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeIdentityRequestRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalRequestId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.type);
            ps.setString(i++, r.userFriendlyType);
            ps.setString(i++, r.state);
            ps.setString(i++, r.source);
            ps.setString(i++, r.sourceObject);
            ps.setString(i++, r.completionStatus);
            ps.setString(i++, r.executionStatus);
            ps.setString(i++, r.priority);
            ps.setString(i++, r.requesterId);
            ps.setString(i++, r.requesterDisplayName);
            ps.setString(i++, r.targetId);
            ps.setString(i++, r.targetDisplayName);
            ps.setString(i++, r.externalTicketId);
            ps.setString(i++, r.processId);
            ps.setString(i++, r.taskResultId);
            setBool(ps, i++, r.executing);
            setBool(ps, i++, r.failure);
            setBool(ps, i++, r.rejected);
            setBool(ps, i++, r.successful);
            setBool(ps, i++, r.terminated);
            setBool(ps, i++, r.incomplete);
            setBool(ps, i++, r.iiqOnly);
            setBool(ps, i++, r.provisioningComplete);
            setTs(ps, i++, r.endDate);
            setTs(ps, i++, r.verified);
            setTs(ps, i++, r.created);
            setTs(ps, i++, r.modified);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.errorsJson);
            setInt(ps, i++, r.itemCount);
            setInt(ps, i++, r.approvalCount);
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
