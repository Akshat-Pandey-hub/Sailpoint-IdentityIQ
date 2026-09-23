package com.keyforge.iiq.event;

import com.keyforge.iiq.config.SchemaName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain-JDBC data access for the append-only CEC {@code kf_event} store. Explicit DDL inside
 * {@code PG_SCHEMA}. Writes are strictly {@code INSERT ... ON CONFLICT (event_id) DO NOTHING} — this
 * class contains no {@code UPDATE} and no {@code DELETE} against {@code kf_event} (append-only, PDF
 * §7.3). Reads over the four source tables are existence-guarded (a missing source table is skipped,
 * mirroring the lineage sidecar), and this class never modifies those source tables.
 */
public class EventRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    private final String schema;
    private final String table;
    private final String workItemArchiveSchema;

    public EventRepository() {
        this(DEFAULT_SCHEMA);
    }

    public EventRepository(String schema) {
        this(schema, schema);
    }

    /** Event target schema and native WorkItemArchive source schema may differ. */
    public EventRepository(String schema, String workItemArchiveSchema) {
        this.schema = SchemaName.validate(schema);
        this.table = this.schema + ".kf_event";
        this.workItemArchiveSchema = SchemaName.validate(workItemArchiveSchema);
    }

    public String schema() {
        return schema;
    }

    public String table() {
        return table;
    }

    String workItemArchiveTable() {
        return workItemArchiveSchema + ".kf_workitem_archive";
    }

    /** Pure DDL builder (unit-testable). */
    public static String createTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "event_id uuid PRIMARY KEY, "
                + "src_system text, "
                + "src_object_type text, "
                + "src_object_id text, "
                + "event_type text, "
                + "event_fingerprint text, "
                + "src_event_ts timestamptz, "
                + "src_event_ts_precision text, "
                + "extraction_run_id text, "
                + "src_interface text, "
                + "raw_ref text, "
                + "event_detail jsonb, "
                + "extracted_at timestamptz NOT NULL DEFAULT now())";
    }

    /**
     * Pure append-only insert builder (unit-testable). {@code raw_ref} is written as a literal
     * {@code NULL} (no RAW zone yet). Conflicts on the deterministic {@code event_id} are ignored, so
     * re-derivation never duplicates and never mutates an existing event.
     */
    public static String appendSql(String table) {
        return "INSERT INTO " + table + " (event_id, src_system, src_object_type, src_object_id, event_type, "
                + "event_fingerprint, src_event_ts, src_event_ts_precision, extraction_run_id, src_interface, "
                + "raw_ref, event_detail) "
                + "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?::jsonb) "
                + "ON CONFLICT (event_id) DO NOTHING "
                + "RETURNING event_id";
    }

    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute(createTableSql(table));
        }
    }

    /**
     * Appends one event. Returns {@code true} if a new row was inserted, {@code false} if an event
     * with the same {@code event_id} already existed (deduplicated, not an error).
     */
    public boolean append(Connection conn, EventRow row) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(appendSql(table))) {
            int i = 1;
            ps.setString(i++, row.eventId());
            ps.setString(i++, row.srcSystem());
            ps.setString(i++, row.srcObjectType());
            ps.setString(i++, row.srcObjectId());
            ps.setString(i++, row.eventType());
            ps.setString(i++, row.eventFingerprint());
            if (row.srcEventTs() == null) {
                ps.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setObject(i++, row.srcEventTs().atOffset(ZoneOffset.UTC));
            }
            ps.setString(i++, row.srcEventTsPrecision());
            ps.setString(i++, row.extractionRunId());
            ps.setString(i++, row.srcInterface());
            ps.setString(i++, row.eventDetailJson());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // a returned event_id means a row was actually inserted
            }
        }
    }

    // --- guarded reads over the four persisted source tables ----------------

    /** Derives every event from the reachable source tables (missing tables are skipped). */
    public List<EventRow> deriveFromSources(Connection conn, String runId) throws SQLException {
        List<EventDeriver.ApprovalSrc> approvals =
                tableExists(conn, "kf_request_approval") ? readApprovals(conn) : List.of();
        List<EventDeriver.TaskSrc> tasks =
                tableExists(conn, "kf_task_result") ? readTasks(conn) : List.of();
        List<EventDeriver.AuditSrc> audits =
                tableExists(conn, "kf_audit_event") ? readAudits(conn) : List.of();
        List<EventDeriver.ProvSrc> provisioning =
                tableExists(conn, "kf_provisioning_txn") ? readProvisioning(conn) : List.of();
        List<EventDeriver.WorkItemArchiveSrc> archives =
                tableExists(conn, workItemArchiveSchema, "kf_workitem_archive")
                        ? readWorkItemArchives(conn) : List.of();
        return EventDeriver.deriveAll(approvals, tasks, audits, provisioning, archives, runId);
    }

    private List<EventDeriver.WorkItemArchiveSrc> readWorkItemArchives(Connection conn) throws SQLException {
        String sql = "SELECT source_id, name, type, state, completer, is_signed, target_name, "
                + "identity_request_id, certification_id, archived_ts FROM "
                + workItemArchiveTable();
        List<EventDeriver.WorkItemArchiveSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new EventDeriver.WorkItemArchiveSrc(
                        rs.getString("source_id"), rs.getString("name"), rs.getString("type"),
                        rs.getString("state"), rs.getString("completer"),
                        (Boolean) rs.getObject("is_signed"), rs.getString("target_name"),
                        rs.getString("identity_request_id"), rs.getString("certification_id"), null,
                        tsUtc(rs, "archived_ts")));
            }
        }
        return out;
    }

    private List<EventDeriver.ApprovalSrc> readApprovals(Connection conn) throws SQLException {
        String sql = "SELECT id, requestid, request_number, owner_display_name, status, open_date, complete_date "
                + "FROM " + schema + ".kf_request_approval";
        List<EventDeriver.ApprovalSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new EventDeriver.ApprovalSrc(
                        rs.getString("id"), rs.getString("requestid"), rs.getString("request_number"),
                        rs.getString("owner_display_name"), rs.getString("status"),
                        tsUtc(rs, "open_date"), tsUtc(rs, "complete_date")));
            }
        }
        return out;
    }

    private List<EventDeriver.TaskSrc> readTasks(Connection conn) throws SQLException {
        String sql = "SELECT taskresultid, completion_status, launched, completed FROM " + schema + ".kf_task_result";
        List<EventDeriver.TaskSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new EventDeriver.TaskSrc(
                        rs.getString("taskresultid"), rs.getString("completion_status"),
                        tsUtc(rs, "launched"), tsUtc(rs, "completed")));
            }
        }
        return out;
    }

    private List<EventDeriver.AuditSrc> readAudits(Connection conn) throws SQLException {
        String sql = "SELECT auditid, action, source, target, created_at, created_display "
                + "FROM " + schema + ".kf_audit_event";
        List<EventDeriver.AuditSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new EventDeriver.AuditSrc(
                        rs.getString("auditid"), rs.getString("action"), rs.getString("source"),
                        rs.getString("target"), tsNaive(rs, "created_at"), rs.getString("created_display")));
            }
        }
        return out;
    }

    private List<EventDeriver.ProvSrc> readProvisioning(Connection conn) throws SQLException {
        String sql = "SELECT txnid, operation, source, status, result, created_at, created_display "
                + "FROM " + schema + ".kf_provisioning_txn";
        List<EventDeriver.ProvSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new EventDeriver.ProvSrc(
                        rs.getString("txnid"), rs.getString("operation"), rs.getString("source"),
                        rs.getString("status"), rs.getString("result"),
                        tsNaive(rs, "created_at"), rs.getString("created_display")));
            }
        }
        return out;
    }

    boolean tableExists(Connection conn, String unqualified) throws SQLException {
        return tableExists(conn, schema, unqualified);
    }

    private static boolean tableExists(Connection conn, String sourceSchema, String unqualified)
            throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceSchema);
            ps.setString(2, unqualified);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /** timestamptz → UTC LocalDateTime (stable across environments). */
    private static LocalDateTime tsUtc(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** naive timestamp (no zone) → LocalDateTime as stored. */
    private static LocalDateTime tsNaive(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDateTime.class);
    }
}
