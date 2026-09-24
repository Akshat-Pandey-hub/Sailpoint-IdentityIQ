package com.keyforge.iiq.reconciliation;

import com.keyforge.iiq.config.SchemaName;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reconciles native approval WorkItem references against the native live-WorkItem and WorkItemArchive
 * data, entirely within the native schema (e.g. {@code iiq_native}). Read-only over the domain tables;
 * the only table it writes is the shared {@code kf_reconciliation_finding} results table (never altered
 * beyond its create-if-absent DDL).
 *
 * <p><b>What it answers:</b> every distinct, non-blank {@code work_item_id} referenced by
 * {@code kf_identity_request_approval} is <em>matched</em> when it exists either as a live WorkItem
 * ({@code kf_workitem.source_id}) or as an archived one ({@code kf_workitem_archive.work_item_id}), and
 * <em>unmatched</em> otherwise. Matching is by EXPLICIT id equality only — never by name, timestamp or
 * ordering. An unmatched reference with zero archives means the completed work item was pruned by IIQ
 * housekeeping and is genuinely unavailable in this instance (not a code defect).
 */
public final class WorkItemReconciliation {

    static final String APPROVAL = "kf_identity_request_approval";
    static final String WORKITEM = "kf_workitem";
    static final String ARCHIVE = "kf_workitem_archive";
    public static final String CHECK_NAME = "approval_workitem_reference";

    private final String schema;

    public WorkItemReconciliation(String schema) {
        this.schema = SchemaName.validate(schema);
    }

    public String schema() {
        return schema;
    }

    /** Aggregate outcome of a reconciliation pass. */
    public static final class Result {
        public boolean skipped;
        public String skipReason;
        public long liveWorkItems;
        public long workItemArchives;
        public long approvalRefs;     // distinct, non-blank work_item_id in approvals
        public long matchedLive;      // refs present as a live WorkItem
        public long matchedArchive;   // refs present as an archived WorkItem
        public long matchedEither;    // refs present in live OR archive
        public long unmatched;        // refs present in neither
        public final List<String> unmatchedSamples = new ArrayList<>();
        public String determination;  // neutral, evidence-only explanation (persisted + printed)
    }

    /**
     * Neutral, evidence-only determination — states what the extracted data shows, never asserts a cause
     * (e.g. never claims records were "pruned by housekeeping"). Only the observable facts.
     */
    static String buildDetermination(Result r) {
        if (r.skipped) {
            return "Reconciliation skipped: " + r.skipReason;
        }
        if (r.approvalRefs == 0) {
            return "No approval work_item_id references present — nothing to reconcile.";
        }
        if (r.unmatched == 0) {
            return "All " + r.approvalRefs + " approval WorkItem reference(s) resolve to a live or archived WorkItem.";
        }
        if (r.workItemArchives == 0) {
            return r.unmatched + " approval WorkItem reference(s) do not resolve to a live WorkItem or "
                    + "WorkItemArchive record. The IIQ instance currently contains zero WorkItemArchive records, "
                    + "so these historical WorkItem references are unavailable in the extracted IIQ data.";
        }
        return r.unmatched + " approval WorkItem reference(s) do not resolve to a live WorkItem or "
                + "WorkItemArchive record. " + r.workItemArchives + " WorkItemArchive record(s) exist but do not "
                + "include these ids, so these historical WorkItem references are unavailable in the extracted IIQ data.";
    }

    // ---- SQL builders (pure, schema-qualified — unit-testable without a DB) ----

    static String qualified(String schema, String table) {
        return schema + "." + table;
    }

    static String countSql(String schema, String table) {
        return "SELECT count(*) FROM " + qualified(schema, table);
    }

    static String distinctRefsSql(String schema) {
        return "SELECT count(DISTINCT work_item_id) FROM " + qualified(schema, APPROVAL)
                + " WHERE work_item_id IS NOT NULL AND work_item_id <> ''";
    }

    private static String refsCte(String schema) {
        return "SELECT DISTINCT work_item_id AS wid FROM " + qualified(schema, APPROVAL)
                + " WHERE work_item_id IS NOT NULL AND work_item_id <> ''";
    }

    static String matchedLiveSql(String schema) {
        return "SELECT count(*) FROM (" + refsCte(schema) + ") r WHERE EXISTS ("
                + "SELECT 1 FROM " + qualified(schema, WORKITEM) + " w WHERE w.source_id = r.wid)";
    }

    static String matchedArchiveSql(String schema) {
        return "SELECT count(*) FROM (" + refsCte(schema) + ") r WHERE EXISTS ("
                + "SELECT 1 FROM " + qualified(schema, ARCHIVE) + " a WHERE a.work_item_id = r.wid)";
    }

    static String matchedEitherSql(String schema) {
        return "SELECT count(*) FROM (" + refsCte(schema) + ") r WHERE "
                + "EXISTS (SELECT 1 FROM " + qualified(schema, WORKITEM) + " w WHERE w.source_id = r.wid) "
                + "OR EXISTS (SELECT 1 FROM " + qualified(schema, ARCHIVE) + " a WHERE a.work_item_id = r.wid)";
    }

    static String unmatchedSamplesSql(String schema, int limit) {
        return "SELECT r.wid FROM (" + refsCte(schema) + ") r WHERE "
                + "NOT EXISTS (SELECT 1 FROM " + qualified(schema, WORKITEM) + " w WHERE w.source_id = r.wid) "
                + "AND NOT EXISTS (SELECT 1 FROM " + qualified(schema, ARCHIVE) + " a WHERE a.work_item_id = r.wid) "
                + "ORDER BY r.wid LIMIT " + limit;
    }

    // ---- execution ----

    public Result reconcile(Connection conn, String runId) throws SQLException {
        Result r = new Result();

        List<String> missing = new ArrayList<>();
        if (!columnExists(conn, APPROVAL, "work_item_id")) {
            missing.add(APPROVAL + ".work_item_id (run extract-native-identity-request-db)");
        }
        if (!columnExists(conn, WORKITEM, "source_id")) {
            missing.add(WORKITEM + ".source_id (run extract-native-workitem-db)");
        }
        if (!columnExists(conn, ARCHIVE, "work_item_id")) {
            missing.add(ARCHIVE + ".work_item_id (run extract-native-workitem-archive-db)");
        }
        if (!missing.isEmpty()) {
            r.skipped = true;
            r.skipReason = "required table/column absent: " + String.join("; ", missing);
            r.determination = buildDetermination(r);
            persist(conn, runId, r);
            return r;
        }

        r.liveWorkItems = scalar(conn, countSql(schema, WORKITEM));
        r.workItemArchives = scalar(conn, countSql(schema, ARCHIVE));
        r.approvalRefs = scalar(conn, distinctRefsSql(schema));
        r.matchedLive = scalar(conn, matchedLiveSql(schema));
        r.matchedArchive = scalar(conn, matchedArchiveSql(schema));
        r.matchedEither = scalar(conn, matchedEitherSql(schema));
        r.unmatched = r.approvalRefs - r.matchedEither;
        if (r.unmatched > 0) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(unmatchedSamplesSql(schema, 25))) {
                while (rs.next()) {
                    r.unmatchedSamples.add(rs.getString(1));
                }
            }
        }
        r.determination = buildDetermination(r);
        persist(conn, runId, r);
        return r;
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private long scalar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /**
     * Persists a single summary finding into the shared {@code kf_reconciliation_finding} table. The
     * neutral determination is stored in an additive {@code note} column (added if absent — never a
     * rename, and the other reconciliation writers simply leave it null).
     */
    private void persist(Connection conn, String runId, Result r) throws SQLException {
        new ReconciliationRepository(schema).ensureTargetTable(conn);
        String target = schema + ".kf_reconciliation_finding";
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + target + " ADD COLUMN IF NOT EXISTS note text");
        }
        UUID id = UUID.nameUUIDFromBytes((runId + "|" + CHECK_NAME).getBytes(StandardCharsets.UTF_8));
        String sql = "INSERT INTO " + target + " (finding_id, run_id, check_name, child_table, child_column, "
                + "parent_table, parent_column, status, skip_reason, orphan_count, sample_ids, severity, note, detected_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?, CAST(? AS jsonb), ?, ?, ?) "
                + "ON CONFLICT (finding_id) DO UPDATE SET run_id=EXCLUDED.run_id, status=EXCLUDED.status, "
                + "skip_reason=EXCLUDED.skip_reason, orphan_count=EXCLUDED.orphan_count, "
                + "sample_ids=EXCLUDED.sample_ids, severity=EXCLUDED.severity, note=EXCLUDED.note, "
                + "detected_at=EXCLUDED.detected_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, runId);
            ps.setString(3, CHECK_NAME);
            ps.setString(4, APPROVAL);
            ps.setString(5, "work_item_id");
            ps.setString(6, WORKITEM + "|" + ARCHIVE);
            ps.setString(7, "source_id|work_item_id");
            ps.setString(8, r.skipped ? "SKIPPED" : "CHECKED");
            ps.setString(9, r.skipReason);
            if (r.skipped) {
                ps.setNull(10, java.sql.Types.BIGINT);
            } else {
                ps.setLong(10, r.unmatched);
            }
            ps.setString(11, sampleJson(r.unmatchedSamples));
            ps.setString(12, r.skipped ? "SKIPPED" : (r.unmatched > 0 ? "INFO" : "OK"));
            ps.setString(13, r.determination);
            ps.setTimestamp(14, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private static String sampleJson(List<String> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(ids.get(i) == null ? "" : ids.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }
}
