package com.keyforge.iiq.nativeevent;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.event.EventRepository;
import com.keyforge.iiq.event.EventRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the native EVENT-zone derivation: read the already-extracted {@code iiq_native} source
 * tables (existence-guarded, never modified), derive append-only events + explicit links via the pure
 * {@link NativeEventDeriver}, and persist them into {@code iiq_native.kf_event} (reusing the CEC
 * {@link EventRepository} structure) and {@code iiq_native.kf_event_link}. Reads NO REST-derived table.
 */
public final class NativeEventDerivationService {

    private final String schema;
    private final EventRepository eventRepo;
    private final NativeEventLinkRepository linkRepo;

    public NativeEventDerivationService(String schema) {
        this.schema = SchemaName.validate(schema);
        this.eventRepo = new EventRepository(this.schema);
        this.linkRepo = new NativeEventLinkRepository(this.schema);
    }

    public String eventTable() {
        return eventRepo.table();
    }

    public String linkTable() {
        return linkRepo.table();
    }

    /** Aggregate outcome. */
    public static final class Result {
        public int approvalRows, taskRows, provRows, archiveRows, certItemRows;
        public final List<String> sourcesMissing = new ArrayList<>();
        public int eventsDerived, eventsInserted;
        public int linksDerived, linksInserted;
        public final Map<String, Integer> eventsByType = new LinkedHashMap<>();
        public final Map<String, Integer> linksByType = new LinkedHashMap<>();
    }

    public Result derive(Connection conn, String runId) throws SQLException {
        eventRepo.ensureTargetTable(conn);
        linkRepo.ensureTargetTable(conn);

        Result r = new Result();

        List<NativeEventDeriver.ApprovalSrc> approvals = new ArrayList<>();
        if (tableExists(conn, "kf_identity_request_approval")) {
            approvals = readApprovals(conn);
            r.approvalRows = approvals.size();
        } else {
            r.sourcesMissing.add("kf_identity_request_approval");
        }

        List<NativeEventDeriver.TaskSrc> tasks = new ArrayList<>();
        if (tableExists(conn, "kf_task_result")) {
            tasks = readTasks(conn);
            r.taskRows = tasks.size();
        } else {
            r.sourcesMissing.add("kf_task_result");
        }

        List<NativeEventDeriver.ProvSrc> provisioning = new ArrayList<>();
        if (tableExists(conn, "kf_provisioning_txn")) {
            provisioning = readProvisioning(conn);
            r.provRows = provisioning.size();
        } else {
            r.sourcesMissing.add("kf_provisioning_txn");
        }

        List<NativeEventDeriver.ArchiveSrc> archives = new ArrayList<>();
        if (tableExists(conn, "kf_workitem_archive")) {
            archives = readArchives(conn);
            r.archiveRows = archives.size();
        } else {
            r.sourcesMissing.add("kf_workitem_archive");
        }

        List<NativeEventDeriver.CertItemSrc> certItems = new ArrayList<>();
        if (tableExists(conn, "kf_certification_item")) {
            certItems = readCertItems(conn);
            r.certItemRows = certItems.size();
        } else {
            r.sourcesMissing.add("kf_certification_item");
        }

        NativeEventDeriver.Derived d =
                NativeEventDeriver.deriveAll(approvals, tasks, provisioning, archives, certItems, runId);

        r.eventsDerived = d.events.size();
        r.linksDerived = d.links.size();
        for (EventRow e : d.events) {
            r.eventsByType.merge(e.eventType(), 1, Integer::sum);
            if (eventRepo.append(conn, e)) {
                r.eventsInserted++;
            }
        }
        for (NativeEventLinkRow l : d.links) {
            r.linksByType.merge(l.linkType(), 1, Integer::sum);
            if (linkRepo.append(conn, l)) {
                r.linksInserted++;
            }
        }
        return r;
    }

    // --- guarded reads over native source tables (native column names) ---

    private List<NativeEventDeriver.ApprovalSrc> readApprovals(Connection conn) throws SQLException {
        String sql = "SELECT identityrequestapprovalid, request_source_id, owner_id, owner, state, approved, "
                + "completer, work_item_id, start_date, end_date FROM " + schema + ".kf_identity_request_approval";
        List<NativeEventDeriver.ApprovalSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new NativeEventDeriver.ApprovalSrc(
                        rs.getString("identityrequestapprovalid"), rs.getString("request_source_id"),
                        rs.getString("owner_id"), rs.getString("owner"), rs.getString("state"),
                        (Boolean) rs.getObject("approved"), rs.getString("completer"), rs.getString("work_item_id"),
                        tsUtc(rs, "start_date"), tsUtc(rs, "end_date")));
            }
        }
        return out;
    }

    private List<NativeEventDeriver.TaskSrc> readTasks(Connection conn) throws SQLException {
        String sql = "SELECT taskresultid, name, type, completion_status, target_class, target_id, "
                + "launched_at, completed_at FROM " + schema + ".kf_task_result";
        List<NativeEventDeriver.TaskSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new NativeEventDeriver.TaskSrc(
                        rs.getString("taskresultid"), rs.getString("name"), rs.getString("type"),
                        rs.getString("completion_status"), rs.getString("target_class"), rs.getString("target_id"),
                        tsUtc(rs, "launched_at"), tsUtc(rs, "completed_at")));
            }
        }
        return out;
    }

    private List<NativeEventDeriver.ProvSrc> readProvisioning(Connection conn) throws SQLException {
        String sql = "SELECT provisioningtxnid, operation, type, status, plan_result_status, identity_name, "
                + "application_name, access_request_id, owner_id, wait_work_item_id, manual_work_item_id, "
                + "created_at FROM " + schema + ".kf_provisioning_txn";
        List<NativeEventDeriver.ProvSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new NativeEventDeriver.ProvSrc(
                        rs.getString("provisioningtxnid"), rs.getString("operation"), rs.getString("type"),
                        rs.getString("status"), rs.getString("plan_result_status"), rs.getString("identity_name"),
                        rs.getString("application_name"), rs.getString("access_request_id"), rs.getString("owner_id"),
                        rs.getString("wait_work_item_id"), rs.getString("manual_work_item_id"),
                        tsUtc(rs, "created_at")));
            }
        }
        return out;
    }

    private List<NativeEventDeriver.ArchiveSrc> readArchives(Connection conn) throws SQLException {
        String sql = "SELECT source_id, name, type, state, completer, is_signed, identity_request_id, "
                + "work_item_id, archived_ts FROM " + schema + ".kf_workitem_archive";
        List<NativeEventDeriver.ArchiveSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new NativeEventDeriver.ArchiveSrc(
                        rs.getString("source_id"), rs.getString("name"), rs.getString("type"),
                        rs.getString("state"), rs.getString("completer"), (Boolean) rs.getObject("is_signed"),
                        rs.getString("identity_request_id"), rs.getString("work_item_id"),
                        tsUtc(rs, "archived_ts")));
            }
        }
        return out;
    }

    private List<NativeEventDeriver.CertItemSrc> readCertItems(Connection conn) throws SQLException {
        String sql = "SELECT certificationitemid, certification_id, entity_id, identity, action_status, "
                + "action_remediation_action, action_actor_name, action_is_approved, action_is_remediation, "
                + "action_is_revoke_account, acted_upon, owner_id, action_decision_date, finished_date, completed "
                + "FROM " + schema + ".kf_certification_item";
        List<NativeEventDeriver.CertItemSrc> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new NativeEventDeriver.CertItemSrc(
                        rs.getString("certificationitemid"), rs.getString("certification_id"),
                        rs.getString("entity_id"), rs.getString("identity"), rs.getString("action_status"),
                        rs.getString("action_remediation_action"), rs.getString("action_actor_name"),
                        (Boolean) rs.getObject("action_is_approved"), (Boolean) rs.getObject("action_is_remediation"),
                        (Boolean) rs.getObject("action_is_revoke_account"), (Boolean) rs.getObject("acted_upon"),
                        rs.getString("owner_id"), tsUtc(rs, "action_decision_date"), tsUtc(rs, "finished_date"),
                        tsUtc(rs, "completed")));
            }
        }
        return out;
    }

    private boolean tableExists(Connection conn, String unqualified) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, unqualified);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static LocalDateTime tsUtc(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
