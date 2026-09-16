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
 * Plain-JDBC data access for referential-integrity reconciliation. Read-only against the domain tables
 * (guarded anti-joins); the only table it writes is its own {@code kf_reconciliation_finding} results
 * table. Never alters or drops any existing table. Same conventions as the other repositories.
 */
public final class ReconciliationRepository {

    public static final String DEFAULT_SCHEMA = SchemaName.DEFAULT;

    private final String schema;
    private final String targetTable;

    public ReconciliationRepository() {
        this(DEFAULT_SCHEMA);
    }

    public ReconciliationRepository(String schema) {
        this.schema = SchemaName.validate(schema);
        this.targetTable = this.schema + ".kf_reconciliation_finding";
    }

    public String schema() {
        return schema;
    }

    public String targetTable() {
        return targetTable;
    }

    /** Creates the schema and results table if absent. Non-destructive. */
    public void ensureTargetTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            st.execute("CREATE TABLE IF NOT EXISTS " + targetTable + " ("
                    + "finding_id uuid PRIMARY KEY, "
                    + "run_id text, "
                    + "check_name text, "
                    + "child_table text, child_column text, "
                    + "parent_table text, parent_column text, "
                    + "status text, skip_reason text, "
                    + "orphan_count bigint, "
                    + "sample_ids jsonb, "
                    + "severity text, "
                    + "detected_at timestamptz)");
        }
    }

    /** True when {@code table.column} exists in this schema (information_schema, bound params). */
    public boolean columnExists(Connection conn, String table, String column) throws SQLException {
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

    /** Runs one check's anti-join and returns [orphanCount, sampleIds]. Caller guards existence first. */
    public ReconciliationFinding runCheck(Connection conn, ReferentialCheck c) throws SQLException {
        long count;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(ReconciliationChecks.countSql(schema, c))) {
            rs.next();
            count = rs.getLong(1);
        }
        List<String> samples = new ArrayList<>();
        if (count > 0) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(ReconciliationChecks.sampleSql(schema, c, 5))) {
                while (rs.next()) {
                    samples.add(rs.getString(1));
                }
            }
        }
        return ReconciliationFinding.checked(c, count, samples);
    }

    /** Deterministic finding id per (runId, checkName) — a real UUID, bound as PostgreSQL {@code uuid}. */
    static UUID findingId(String runId, String checkName) {
        return UUID.nameUUIDFromBytes((runId + "|" + checkName).getBytes(StandardCharsets.UTF_8));
    }

    /** Upserts one finding, keyed by a deterministic id per (runId, checkName). */
    public void upsert(Connection conn, String runId, ReconciliationFinding f, Instant detectedAt)
            throws SQLException {
        UUID findingId = findingId(runId, f.check().name());
        String sql = "INSERT INTO " + targetTable + " (finding_id, run_id, check_name, child_table, "
                + "child_column, parent_table, parent_column, status, skip_reason, orphan_count, "
                + "sample_ids, severity, detected_at) VALUES (?,?,?,?,?,?,?,?,?,?, CAST(? AS jsonb), ?, ?) "
                + "ON CONFLICT (finding_id) DO UPDATE SET run_id=EXCLUDED.run_id, status=EXCLUDED.status, "
                + "skip_reason=EXCLUDED.skip_reason, orphan_count=EXCLUDED.orphan_count, "
                + "sample_ids=EXCLUDED.sample_ids, severity=EXCLUDED.severity, detected_at=EXCLUDED.detected_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ReferentialCheck c = f.check();
            ps.setObject(1, findingId);   // bind java.util.UUID -> PostgreSQL uuid (not varchar)
            ps.setString(2, runId);
            ps.setString(3, c.name());
            ps.setString(4, c.childTable());
            ps.setString(5, c.childColumn());
            ps.setString(6, c.parentTable());
            ps.setString(7, c.parentColumn());
            ps.setString(8, f.status());
            ps.setString(9, f.skipReason());
            if (f.orphanCount() < 0) {
                ps.setNull(10, java.sql.Types.BIGINT);
            } else {
                ps.setLong(10, f.orphanCount());
            }
            ps.setString(11, sampleJson(f.sampleIds()));
            ps.setString(12, c.severity());
            ps.setTimestamp(13, Timestamp.from(detectedAt));
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
