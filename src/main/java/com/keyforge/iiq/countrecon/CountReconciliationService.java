package com.keyforge.iiq.countrecon;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-pipeline count reconciliation: compares each domain's PostgreSQL row count with its Parquet
 * dataset row count and records the outcome in {@code kf_count_reconciliation}. Read-only over both
 * stores; the PDF's count / cross-pipeline reconciliation. Does not touch extraction, lineage, or the
 * referential-integrity reconciliation.
 */
public final class CountReconciliationService {

    private final CountReconciliationRepository repo;

    public CountReconciliationService(CountReconciliationRepository repo) {
        this.repo = repo;
    }

    public CountReconciliationRepository repository() {
        return repo;
    }

    public record DomainResult(CountPair pair, long pgCount, long parquetCount, String status) {
    }

    public static final class Result {
        private final List<DomainResult> domains = new ArrayList<>();
        private int matches;
        private int mismatches;
        private int other;
        private int persistFailures;

        public List<DomainResult> domains() { return domains; }
        public int matches() { return matches; }
        public int mismatches() { return mismatches; }
        public int other() { return other; }
        public int persistFailures() { return persistFailures; }
    }

    public Result reconcile(Connection conn, String runId) throws SQLException {
        repo.ensureTargetTable(conn);
        Instant now = Instant.now();
        Result result = new Result();
        for (CountPair pair : CountReconciliation.all()) {
            long pg = repo.pgCount(conn, pair.pgTable());
            long pq = repo.parquetCount(pair.parquetDataset());
            String status = CountReconciliation.classify(pg, pq);
            result.domains().add(new DomainResult(pair, pg, pq, status));
            if (CountReconciliation.MATCH.equals(status)) {
                result.matches++;
            } else if (CountReconciliation.MISMATCH.equals(status)) {
                result.mismatches++;
            } else {
                result.other++;
            }
            try {
                repo.upsert(conn, runId, pair, pg, pq, status, now);
            } catch (SQLException e) {
                result.persistFailures++;
                System.err.println("[count-reconcile] failed to persist '" + pair.domain() + "': " + e.getMessage());
            }
        }
        return result;
    }
}
