package com.keyforge.iiq.reconciliation;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the referential-integrity check catalog against the normalized PostgreSQL schema and persists
 * the findings to {@code kf_reconciliation_finding}. This is the PDF's reconciliation / data-quality
 * plane (dangling-reference detection): it reads the existing tables and never modifies them, so it is
 * safe to run at any time and does not touch any extraction path.
 *
 * <p>Each check is guarded: if the child or parent table/column is absent in the target schema the
 * check is recorded as SKIPPED (with the reason) rather than failing the run or reporting a false
 * zero — so the same catalog works across the legacy and canonical schema shapes.
 */
public final class ReferentialIntegrityService {

    private final ReconciliationRepository repo;

    public ReferentialIntegrityService() {
        this(new ReconciliationRepository());
    }

    public ReferentialIntegrityService(String schema) {
        this(new ReconciliationRepository(schema));
    }

    public ReferentialIntegrityService(ReconciliationRepository repo) {
        this.repo = repo;
    }

    public ReconciliationRepository repository() {
        return repo;
    }

    /** Aggregate outcome of a reconciliation pass. */
    public static final class Result {
        private final List<ReconciliationFinding> findings = new ArrayList<>();
        private int checked;
        private int skipped;
        private int checksWithOrphans;
        private long totalOrphans;
        private int persistFailures;

        public List<ReconciliationFinding> findings() { return findings; }
        public int total() { return findings.size(); }
        public int checked() { return checked; }
        public int skipped() { return skipped; }
        public int checksWithOrphans() { return checksWithOrphans; }
        public long totalOrphans() { return totalOrphans; }
        public int persistFailures() { return persistFailures; }
    }

    public Result reconcile(Connection conn, String runId) throws SQLException {
        repo.ensureTargetTable(conn);
        Instant now = Instant.now();
        Result result = new Result();
        for (ReferentialCheck c : ReconciliationChecks.all()) {
            ReconciliationFinding finding = evaluate(conn, c);
            result.findings().add(finding);
            if (ReconciliationFinding.SKIPPED.equals(finding.status())) {
                result.skipped++;
            } else {
                result.checked++;
                if (finding.orphanCount() > 0) {
                    result.checksWithOrphans++;
                    result.totalOrphans += finding.orphanCount();
                }
            }
            try {
                repo.upsert(conn, runId, finding, now);
            } catch (SQLException e) {
                result.persistFailures++;
                System.err.println("[reconcile] failed to persist finding '" + c.name() + "': " + e.getMessage());
            }
        }
        return result;
    }

    /** Guards table/column existence, then runs the check; SKIPPED (not failed) when absent. */
    private ReconciliationFinding evaluate(Connection conn, ReferentialCheck c) throws SQLException {
        if (!repo.columnExists(conn, c.childTable(), c.childColumn())) {
            return ReconciliationFinding.skipped(c, "child column absent: " + c.childTable() + "." + c.childColumn());
        }
        if (!repo.columnExists(conn, c.parentTable(), c.parentColumn())) {
            return ReconciliationFinding.skipped(c, "parent column absent: " + c.parentTable() + "." + c.parentColumn());
        }
        return repo.runCheck(conn, c);
    }
}
