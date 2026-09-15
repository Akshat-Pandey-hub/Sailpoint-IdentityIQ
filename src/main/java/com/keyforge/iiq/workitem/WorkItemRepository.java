package com.keyforge.iiq.workitem;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the project-owned {@code workitem} migration table.
 * Explicit DDL inside {@code PG_SCHEMA} (no ISPM template). Idempotent upsert on the
 * primary key {@code id}, so re-running never duplicates.
 */
public class WorkItemRepository {

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

    public WorkItemRepository() {
        this(DEFAULT_SCHEMA);
    }

    public WorkItemRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".workitem";
        this.createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + this.schema;
        this.createTableSql =
                "CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                        + "id uuid PRIMARY KEY, "
                        + "work_item_name text, "
                        + "work_item_type text, "
                        + "work_item_state text, "
                        + "access_request_name text, "
                        + "description text, "
                        + "priority text, "
                        + "comment_count integer, "
                        + "editable boolean, "
                        + "reminders integer, "
                        + "escalation_count integer, "
                        + "completion_comments text, "
                        + "esig_meaning text, "
                        + "certification_id text, "
                        + "disable_forwarding boolean, "
                        + "force_classic_approval_ui boolean, "
                        + "new_type_work_item boolean, "
                        + "owner_id uuid, owner_name text, owner_display_name text, "
                        + "requester_id uuid, requester_name text, requester_display_name text, "
                        + "assignee_id uuid, assignee_name text, assignee_display_name text, "
                        + "target_id uuid, target_name text, target_display_name text, "
                        + "created_at timestamptz, "
                        + "notification_date timestamptz, "
                        + "expiration_date timestamptz, "
                        + "wake_up_date timestamptz, "
                        + "extracted_at timestamptz NOT NULL DEFAULT now()"
                        + ")";
        this.upsertSql =
                "INSERT INTO " + targetTable + " ("
                        + "id, work_item_name, work_item_type, work_item_state, access_request_name, description, "
                        + "priority, comment_count, editable, reminders, escalation_count, completion_comments, "
                        + "esig_meaning, certification_id, disable_forwarding, force_classic_approval_ui, "
                        + "new_type_work_item, owner_id, owner_name, owner_display_name, requester_id, "
                        + "requester_name, requester_display_name, assignee_id, assignee_name, "
                        + "assignee_display_name, target_id, target_name, target_display_name, created_at, "
                        + "notification_date, expiration_date, wake_up_date) "
                        + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?::uuid, ?, ?, ?::uuid, ?, ?, ?::uuid, ?, ?, ?::uuid, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET "
                        + "work_item_name = EXCLUDED.work_item_name, work_item_type = EXCLUDED.work_item_type, "
                        + "work_item_state = EXCLUDED.work_item_state, "
                        + "access_request_name = EXCLUDED.access_request_name, description = EXCLUDED.description, "
                        + "priority = EXCLUDED.priority, comment_count = EXCLUDED.comment_count, "
                        + "editable = EXCLUDED.editable, reminders = EXCLUDED.reminders, "
                        + "escalation_count = EXCLUDED.escalation_count, "
                        + "completion_comments = EXCLUDED.completion_comments, esig_meaning = EXCLUDED.esig_meaning, "
                        + "certification_id = EXCLUDED.certification_id, "
                        + "disable_forwarding = EXCLUDED.disable_forwarding, "
                        + "force_classic_approval_ui = EXCLUDED.force_classic_approval_ui, "
                        + "new_type_work_item = EXCLUDED.new_type_work_item, owner_id = EXCLUDED.owner_id, "
                        + "owner_name = EXCLUDED.owner_name, owner_display_name = EXCLUDED.owner_display_name, "
                        + "requester_id = EXCLUDED.requester_id, requester_name = EXCLUDED.requester_name, "
                        + "requester_display_name = EXCLUDED.requester_display_name, "
                        + "assignee_id = EXCLUDED.assignee_id, assignee_name = EXCLUDED.assignee_name, "
                        + "assignee_display_name = EXCLUDED.assignee_display_name, target_id = EXCLUDED.target_id, "
                        + "target_name = EXCLUDED.target_name, target_display_name = EXCLUDED.target_display_name, "
                        + "created_at = EXCLUDED.created_at, notification_date = EXCLUDED.notification_date, "
                        + "expiration_date = EXCLUDED.expiration_date, wake_up_date = EXCLUDED.wake_up_date, "
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

    public UpsertOutcome upsert(Connection conn, WorkItemRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            int i = 1;
            ps.setString(i++, row.id());
            ps.setString(i++, row.workItemName());
            ps.setString(i++, row.workItemType());
            ps.setString(i++, row.workItemState());
            ps.setString(i++, row.accessRequestName());
            ps.setString(i++, row.description());
            ps.setString(i++, row.priority());
            setInt(ps, i++, row.commentCount());
            setBool(ps, i++, row.editable());
            setInt(ps, i++, row.reminders());
            setInt(ps, i++, row.escalationCount());
            ps.setString(i++, row.completionComments());
            ps.setString(i++, row.esigMeaning());
            ps.setString(i++, row.certificationId());
            setBool(ps, i++, row.disableForwarding());
            setBool(ps, i++, row.forceClassicApprovalUi());
            setBool(ps, i++, row.newTypeWorkItem());
            ps.setString(i++, row.ownerId());
            ps.setString(i++, row.ownerName());
            ps.setString(i++, row.ownerDisplayName());
            ps.setString(i++, row.requesterId());
            ps.setString(i++, row.requesterName());
            ps.setString(i++, row.requesterDisplayName());
            ps.setString(i++, row.assigneeId());
            ps.setString(i++, row.assigneeName());
            ps.setString(i++, row.assigneeDisplayName());
            ps.setString(i++, row.targetId());
            ps.setString(i++, row.targetName());
            ps.setString(i++, row.targetDisplayName());
            setTimestamp(ps, i++, row.createdAt());
            setTimestamp(ps, i++, row.notificationDate());
            setTimestamp(ps, i++, row.expirationDate());
            setTimestamp(ps, i++, row.wakeUpDate());

            try (ResultSet rs = ps.executeQuery()) {
                boolean inserted = rs.next() && rs.getBoolean("inserted");
                return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
            }
        }
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setBool(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
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
