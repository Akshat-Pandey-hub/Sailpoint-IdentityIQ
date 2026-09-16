package com.keyforge.iiq.lineage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives the PDF lineage envelope for every normalized domain record into the shared
 * {@code kf_record_lineage} sidecar. Reads the existing tables (read-only) and never modifies them,
 * so it is safe to run at any time and does not touch any extraction path. Each catalog table is
 * guarded: if the table or its PK column is absent it is SKIPPED (recorded honestly) rather than
 * failing the run — so the same catalog works across the legacy and canonical schema shapes.
 *
 * <p>Re-runnable: rows upsert on a deterministic {@code lineage_id}, so a fresh run after new
 * extractions refreshes the envelope (this is a derived snapshot, like the other {@code derive-*}
 * commands — not write-time stamping).
 */
public final class RecordLineageService {

    private final RecordLineageRepository repo;

    public RecordLineageService() {
        this(new RecordLineageRepository());
    }

    public RecordLineageService(String schema) {
        this(new RecordLineageRepository(schema));
    }

    public RecordLineageService(RecordLineageRepository repo) {
        this.repo = repo;
    }

    public RecordLineageRepository repository() {
        return repo;
    }

    public record TableResult(String table, String status, int rows, String note) {
        public static final String BACKFILLED = "BACKFILLED";
        public static final String SKIPPED = "SKIPPED";
        public static final String FAILED = "FAILED";
    }

    public static final class Result {
        private final List<TableResult> tables = new ArrayList<>();
        private int backfilled;
        private int skipped;
        private int failed;
        private int totalRows;

        public List<TableResult> tables() { return tables; }
        public int backfilledTables() { return backfilled; }
        public int skippedTables() { return skipped; }
        public int failedTables() { return failed; }
        public int totalRows() { return totalRows; }
    }

    public Result deriveAll(Connection conn) throws SQLException {
        repo.ensureTargetTable(conn);
        Result result = new Result();
        for (LineageSource s : LineageCatalog.all()) {
            if (!repo.tableExists(conn, s.table())) {
                result.tables().add(new TableResult(s.table(), TableResult.SKIPPED, 0, "table absent"));
                result.skipped++;
                continue;
            }
            if (!repo.columnExists(conn, s.table(), s.pk())) {
                result.tables().add(new TableResult(s.table(), TableResult.SKIPPED, 0, "pk column absent: " + s.pk()));
                result.skipped++;
                continue;
            }
            try {
                int rows = repo.backfill(conn, s);
                result.tables().add(new TableResult(s.table(), TableResult.BACKFILLED, rows, null));
                result.backfilled++;
                result.totalRows += rows;
            } catch (SQLException e) {
                result.tables().add(new TableResult(s.table(), TableResult.FAILED, 0, e.getMessage()));
                result.failed++;
                System.err.println("[lineage] failed to backfill '" + s.table() + "': " + e.getMessage());
            }
        }
        return result;
    }
}
