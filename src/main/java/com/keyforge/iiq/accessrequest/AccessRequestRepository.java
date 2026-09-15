package com.keyforge.iiq.accessrequest;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Plain-JDBC data access for the Access Request aggregate: {@code kf_access_request},
 * {@code kf_request_item}, {@code kf_request_approval}. Explicit DDL inside {@code PG_SCHEMA};
 * idempotent upsert on each table's PK.
 */
public class AccessRequestRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    public enum UpsertOutcome { INSERTED, UPDATED }

    private final String schema;
    private final String requestTable;
    private final String itemTable;
    private final String approvalTable;

    public AccessRequestRepository() {
        this(DEFAULT_SCHEMA);
    }

    public AccessRequestRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.requestTable = this.schema + ".kf_access_request";
        this.itemTable = this.schema + ".kf_request_item";
        this.approvalTable = this.schema + ".kf_request_approval";
    }

    public String schema() {
        return schema;
    }

    public String requestTable() {
        return requestTable;
    }

    public String itemTable() {
        return itemTable;
    }

    public String approvalTable() {
        return approvalTable;
    }

    public void ensureTargetTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + requestTable + " ("
                    + "requestid uuid PRIMARY KEY, request_number text, type text, "
                    + "requester_display_name text, target_display_name text, state text, "
                    + "execution_status text, completion_status text, priority text, external_ticket_id text, "
                    + "cancelable boolean, created_at timestamptz, end_date timestamptz, terminated_date timestamptz, "
                    + "verification_date timestamptz, item_count integer, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
            st.execute("CREATE TABLE IF NOT EXISTS " + itemTable + " ("
                    + "itemid uuid PRIMARY KEY, requestid uuid, request_number text, operation text, "
                    + "application_name text, account_name text, displayable_account_name text, instance text, "
                    + "name text, value text, displayable_value text, is_role boolean, is_entitlement boolean, "
                    + "has_managed_attribute boolean, approval_state text, provisioning_state text, "
                    + "provisioning_engine text, assignment_id text, retries integer, requester_comments text, "
                    + "start_date timestamptz, end_date timestamptz, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
            st.execute("CREATE TABLE IF NOT EXISTS " + approvalTable + " ("
                    + "id uuid PRIMARY KEY, requestid uuid, request_number text, owner_display_name text, "
                    + "status text, description text, comments text, approval_item_count integer, "
                    + "work_item_id uuid, work_item_name text, work_item_archive_id text, "
                    + "open_date timestamptz, complete_date timestamptz, "
                    + "extracted_at timestamptz NOT NULL DEFAULT now())");
        }
    }

    public UpsertOutcome upsertRequest(Connection conn, AccessRequestRow r) throws SQLException {
        String sql = "INSERT INTO " + requestTable + " (requestid, request_number, type, requester_display_name, "
                + "target_display_name, state, execution_status, completion_status, priority, external_ticket_id, "
                + "cancelable, created_at, end_date, terminated_date, verification_date, item_count) "
                + "VALUES (?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (requestid) DO UPDATE SET request_number=EXCLUDED.request_number, type=EXCLUDED.type, "
                + "requester_display_name=EXCLUDED.requester_display_name, target_display_name=EXCLUDED.target_display_name, "
                + "state=EXCLUDED.state, execution_status=EXCLUDED.execution_status, "
                + "completion_status=EXCLUDED.completion_status, priority=EXCLUDED.priority, "
                + "external_ticket_id=EXCLUDED.external_ticket_id, cancelable=EXCLUDED.cancelable, "
                + "created_at=EXCLUDED.created_at, end_date=EXCLUDED.end_date, terminated_date=EXCLUDED.terminated_date, "
                + "verification_date=EXCLUDED.verification_date, item_count=EXCLUDED.item_count, extracted_at=now() "
                + "RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.requestid());
            ps.setString(2, r.requestNumber());
            ps.setString(3, r.type());
            ps.setString(4, r.requesterDisplayName());
            ps.setString(5, r.targetDisplayName());
            ps.setString(6, r.state());
            ps.setString(7, r.executionStatus());
            ps.setString(8, r.completionStatus());
            ps.setString(9, r.priority());
            ps.setString(10, r.externalTicketId());
            setBool(ps, 11, r.cancelable());
            setTs(ps, 12, r.createdAt());
            setTs(ps, 13, r.endDate());
            setTs(ps, 14, r.terminatedDate());
            setTs(ps, 15, r.verificationDate());
            setInt(ps, 16, r.itemCount());
            return outcome(ps);
        }
    }

    public UpsertOutcome upsertItem(Connection conn, RequestItemRow r) throws SQLException {
        String sql = "INSERT INTO " + itemTable + " (itemid, requestid, request_number, operation, application_name, "
                + "account_name, displayable_account_name, instance, name, value, displayable_value, is_role, "
                + "is_entitlement, has_managed_attribute, approval_state, provisioning_state, provisioning_engine, "
                + "assignment_id, retries, requester_comments, start_date, end_date) "
                + "VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (itemid) DO UPDATE SET requestid=EXCLUDED.requestid, request_number=EXCLUDED.request_number, "
                + "operation=EXCLUDED.operation, application_name=EXCLUDED.application_name, account_name=EXCLUDED.account_name, "
                + "displayable_account_name=EXCLUDED.displayable_account_name, instance=EXCLUDED.instance, name=EXCLUDED.name, "
                + "value=EXCLUDED.value, displayable_value=EXCLUDED.displayable_value, is_role=EXCLUDED.is_role, "
                + "is_entitlement=EXCLUDED.is_entitlement, has_managed_attribute=EXCLUDED.has_managed_attribute, "
                + "approval_state=EXCLUDED.approval_state, provisioning_state=EXCLUDED.provisioning_state, "
                + "provisioning_engine=EXCLUDED.provisioning_engine, assignment_id=EXCLUDED.assignment_id, "
                + "retries=EXCLUDED.retries, requester_comments=EXCLUDED.requester_comments, start_date=EXCLUDED.start_date, "
                + "end_date=EXCLUDED.end_date, extracted_at=now() RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.itemid());
            ps.setString(2, r.requestid());
            ps.setString(3, r.requestNumber());
            ps.setString(4, r.operation());
            ps.setString(5, r.applicationName());
            ps.setString(6, r.accountName());
            ps.setString(7, r.displayableAccountName());
            ps.setString(8, r.instance());
            ps.setString(9, r.name());
            ps.setString(10, r.value());
            ps.setString(11, r.displayableValue());
            setBool(ps, 12, r.isRole());
            setBool(ps, 13, r.isEntitlement());
            setBool(ps, 14, r.hasManagedAttribute());
            ps.setString(15, r.approvalState());
            ps.setString(16, r.provisioningState());
            ps.setString(17, r.provisioningEngine());
            ps.setString(18, r.assignmentId());
            setInt(ps, 19, r.retries());
            ps.setString(20, r.requesterComments());
            setTs(ps, 21, r.startDate());
            setTs(ps, 22, r.endDate());
            return outcome(ps);
        }
    }

    public UpsertOutcome upsertApproval(Connection conn, RequestApprovalRow r) throws SQLException {
        String sql = "INSERT INTO " + approvalTable + " (id, requestid, request_number, owner_display_name, status, "
                + "description, comments, approval_item_count, work_item_id, work_item_name, work_item_archive_id, "
                + "open_date, complete_date) VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?::uuid,?,?,?,?) "
                + "ON CONFLICT (id) DO UPDATE SET requestid=EXCLUDED.requestid, request_number=EXCLUDED.request_number, "
                + "owner_display_name=EXCLUDED.owner_display_name, status=EXCLUDED.status, description=EXCLUDED.description, "
                + "comments=EXCLUDED.comments, approval_item_count=EXCLUDED.approval_item_count, "
                + "work_item_id=EXCLUDED.work_item_id, work_item_name=EXCLUDED.work_item_name, "
                + "work_item_archive_id=EXCLUDED.work_item_archive_id, open_date=EXCLUDED.open_date, "
                + "complete_date=EXCLUDED.complete_date, extracted_at=now() RETURNING (xmax = 0) AS inserted";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.id());
            ps.setString(2, r.requestid());
            ps.setString(3, r.requestNumber());
            ps.setString(4, r.ownerDisplayName());
            ps.setString(5, r.status());
            ps.setString(6, r.description());
            ps.setString(7, r.comments());
            setInt(ps, 8, r.approvalItemCount());
            ps.setString(9, r.workItemId());
            ps.setString(10, r.workItemName());
            ps.setString(11, r.workItemArchiveId());
            setTs(ps, 12, r.openDate());
            setTs(ps, 13, r.completeDate());
            return outcome(ps);
        }
    }

    private static UpsertOutcome outcome(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            boolean inserted = rs.next() && rs.getBoolean("inserted");
            return inserted ? UpsertOutcome.INSERTED : UpsertOutcome.UPDATED;
        }
    }

    private static void setBool(PreparedStatement ps, int i, Boolean v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BOOLEAN); else ps.setBoolean(i, v);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    private static void setTs(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP); else ps.setObject(i, v.atOffset(java.time.ZoneOffset.UTC));
    }
}
