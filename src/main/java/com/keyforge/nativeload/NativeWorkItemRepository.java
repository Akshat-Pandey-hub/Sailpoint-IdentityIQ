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
 * Plain-JDBC persistence for native WorkItem rows into {@code <schema>.kf_workitem} (current-state:
 * idempotent upsert on the deterministic canonical UUID of the WorkItem id; soft-delete via the shared
 * sweeper). Business-content {@code record_hash} for change detection. The four nested arrays
 * ({@code comments}, {@code signOffs}, {@code ownerHistory}, {@code approvalSetItems}) are persisted as
 * jsonb.
 */
public final class NativeWorkItemRepository {

    private final String schema;
    private final String targetTable;
    private final String createSchemaSql;
    private final String createTableSql;
    private final String upsertSql;

    public enum UpsertOutcome { INSERTED, UPDATED }

    public NativeWorkItemRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_workitem";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "workitemid uuid PRIMARY KEY, source_id text, name text, type text, state text, "
                        + "level text, requester_id text, requester_name text, assignee_id text, assignee_name text, "
                        + "owner_id text, owner_name text, completer text, completion_comments text, handler text, "
                        + "notification_name text, identity_request_id text, target_id text, target_name text, "
                        + "certification_id text, certification_entity_id text, certification_item_id text, "
                        + "entity_type text, certification_related boolean, workflow_case_id text, "
                        + "workflow_case_name text, expiration timestamptz, expiration_date timestamptz, "
                        + "notification timestamptz, wake_up_date timestamptz, escalation_count integer, "
                        + "reminders integer, reminders_sent integer, expired boolean, expirable boolean, "
                        + "approval_set_item_count integer, comments jsonb, sign_offs jsonb, owner_history jsonb, "
                        + "approval_set_items jsonb, created_at timestamptz, modified_at timestamptz, record_hash text, "
                        + "source_system text, source_interface text, source_object_type text, extraction_run_id text, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now())";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "workitemid, source_id, name, type, state, level, requester_id, requester_name, "
                        + "assignee_id, assignee_name, owner_id, owner_name, completer, completion_comments, handler, "
                        + "notification_name, identity_request_id, target_id, target_name, certification_id, "
                        + "certification_entity_id, certification_item_id, entity_type, certification_related, "
                        + "workflow_case_id, workflow_case_name, expiration, expiration_date, notification, "
                        + "wake_up_date, escalation_count, reminders, reminders_sent, expired, expirable, "
                        + "approval_set_item_count, comments, sign_offs, owner_history, approval_set_items, "
                        + "created_at, modified_at, record_hash, source_system, source_interface, source_object_type, "
                        + "extraction_run_id) "
                        + "VALUES (?::uuid, "
                        + String.join(", ", java.util.Collections.nCopies(35, "?"))
                        + ", ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, "
                        + String.join(", ", java.util.Collections.nCopies(7, "?"))
                        + ") "
                        + "ON CONFLICT (workitemid) DO UPDATE SET "
                        + "source_id = EXCLUDED.source_id, name = EXCLUDED.name, type = EXCLUDED.type, "
                        + "state = EXCLUDED.state, level = EXCLUDED.level, requester_id = EXCLUDED.requester_id, "
                        + "requester_name = EXCLUDED.requester_name, assignee_id = EXCLUDED.assignee_id, "
                        + "assignee_name = EXCLUDED.assignee_name, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, completer = EXCLUDED.completer, "
                        + "completion_comments = EXCLUDED.completion_comments, handler = EXCLUDED.handler, "
                        + "notification_name = EXCLUDED.notification_name, "
                        + "identity_request_id = EXCLUDED.identity_request_id, target_id = EXCLUDED.target_id, "
                        + "target_name = EXCLUDED.target_name, certification_id = EXCLUDED.certification_id, "
                        + "certification_entity_id = EXCLUDED.certification_entity_id, "
                        + "certification_item_id = EXCLUDED.certification_item_id, entity_type = EXCLUDED.entity_type, "
                        + "certification_related = EXCLUDED.certification_related, "
                        + "workflow_case_id = EXCLUDED.workflow_case_id, "
                        + "workflow_case_name = EXCLUDED.workflow_case_name, expiration = EXCLUDED.expiration, "
                        + "expiration_date = EXCLUDED.expiration_date, notification = EXCLUDED.notification, "
                        + "wake_up_date = EXCLUDED.wake_up_date, escalation_count = EXCLUDED.escalation_count, "
                        + "reminders = EXCLUDED.reminders, reminders_sent = EXCLUDED.reminders_sent, "
                        + "expired = EXCLUDED.expired, expirable = EXCLUDED.expirable, "
                        + "approval_set_item_count = EXCLUDED.approval_set_item_count, comments = EXCLUDED.comments, "
                        + "sign_offs = EXCLUDED.sign_offs, owner_history = EXCLUDED.owner_history, "
                        + "approval_set_items = EXCLUDED.approval_set_items, created_at = EXCLUDED.created_at, "
                        + "modified_at = EXCLUDED.modified_at, record_hash = EXCLUDED.record_hash, "
                        + "source_system = EXCLUDED.source_system, source_interface = EXCLUDED.source_interface, "
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

    public static String canonicalWorkItemId(NativeWorkItemRecord r) {
        String id = ParquetIds.canonicalUuid(r.sourceId);
        if (id == null) {
            throw new IllegalArgumentException("WorkItem source id is required for deterministic identity");
        }
        return id;
    }

    public static String recordHash(NativeWorkItemRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("source_id", r.sourceId);
        b.put("name", r.name);
        b.put("type", r.type);
        b.put("state", r.state);
        b.put("level", r.level);
        b.put("requester_id", r.requesterId);
        b.put("assignee_id", r.assigneeId);
        b.put("owner_id", r.ownerId);
        b.put("owner_name", r.ownerName);
        b.put("completer", r.completer);
        b.put("completion_comments", r.completionComments);
        b.put("identity_request_id", r.identityRequestId);
        b.put("target_id", r.targetId);
        b.put("target_name", r.targetName);
        b.put("certification_id", r.certificationId);
        b.put("certification_related", r.certificationRelated);
        b.put("workflow_case_id", r.workflowCaseId);
        b.put("expiration", r.expiration);
        b.put("expiration_date", r.expirationDate);
        b.put("escalation_count", r.escalationCount);
        b.put("reminders", r.reminders);
        b.put("reminders_sent", r.remindersSent);
        b.put("expired", r.expired);
        b.put("expirable", r.expirable);
        b.put("approval_set_item_count", r.approvalSetItemCount);
        b.put("comments", r.commentsJson);
        b.put("sign_offs", r.signOffsJson);
        b.put("owner_history", r.ownerHistoryJson);
        b.put("approval_set_items", r.approvalSetItemsJson);
        b.put("created_at", r.created);
        return NativeRecordHash.of(b);
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(createSchemaSql);
            st.execute(createTableSql);
        }
    }

    public UpsertOutcome upsert(Connection conn, NativeWorkItemRecord r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, canonicalWorkItemId(r));
            ps.setString(i++, r.sourceId);
            ps.setString(i++, r.name);
            ps.setString(i++, r.type);
            ps.setString(i++, r.state);
            ps.setString(i++, r.level);
            ps.setString(i++, r.requesterId);
            ps.setString(i++, r.requesterName);
            ps.setString(i++, r.assigneeId);
            ps.setString(i++, r.assigneeName);
            ps.setString(i++, r.ownerId);
            ps.setString(i++, r.ownerName);
            ps.setString(i++, r.completer);
            ps.setString(i++, r.completionComments);
            ps.setString(i++, r.handler);
            ps.setString(i++, r.notificationName);
            ps.setString(i++, r.identityRequestId);
            ps.setString(i++, r.targetId);
            ps.setString(i++, r.targetName);
            ps.setString(i++, r.certificationId);
            ps.setString(i++, r.certificationEntityId);
            ps.setString(i++, r.certificationItemId);
            ps.setString(i++, r.entityType);
            setBool(ps, i++, r.certificationRelated);
            ps.setString(i++, r.workflowCaseId);
            ps.setString(i++, r.workflowCaseName);
            setTs(ps, i++, r.expiration);
            setTs(ps, i++, r.expirationDate);
            setTs(ps, i++, r.notification);
            setTs(ps, i++, r.wakeUpDate);
            setInt(ps, i++, r.escalationCount);
            setInt(ps, i++, r.reminders);
            setInt(ps, i++, r.remindersSent);
            setBool(ps, i++, r.expired);
            setBool(ps, i++, r.expirable);
            setInt(ps, i++, r.approvalSetItemCount);
            ps.setString(i++, r.commentsJson);
            ps.setString(i++, r.signOffsJson);
            ps.setString(i++, r.ownerHistoryJson);
            ps.setString(i++, r.approvalSetItemsJson);
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
