package com.keyforge.nativeload;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Orchestrates one SQL-report import: fetch the plugin JSON (the business SQL's result, executed inside
 * IIQ), parse it, and replace-load it into {@link NativeSqlReportRepository}. Pure orchestration — no
 * HTTP and no JDBC wiring of its own (the fetcher and connection are injected), so it is unit-testable
 * with canned JSON and no live server.
 */
public final class NativeSqlReportImportService {

    public static final class Result {
        private final int returned;
        private final int persisted;
        private final boolean truncated;
        private final int columnCount;

        Result(int returned, int persisted, boolean truncated, int columnCount) {
            this.returned = returned;
            this.persisted = persisted;
            this.truncated = truncated;
            this.columnCount = columnCount;
        }

        public int getReturned() { return returned; }
        public int getPersisted() { return persisted; }
        public boolean isTruncated() { return truncated; }
        public int getColumnCount() { return columnCount; }
    }

    private final Supplier<String> fetcher;
    private final NativeSqlReportParser parser = new NativeSqlReportParser();
    private final NativeSqlReportRepository repository;

    public NativeSqlReportImportService(Supplier<String> fetcher, NativeSqlReportRepository repository) {
        this.fetcher = fetcher;
        this.repository = repository;
    }

    /** Fetch → parse → replace-all. Returns row counts; the table ends equal to the query result. */
    public Result importAll(Connection conn, String runId) throws SQLException {
        String json = fetcher.get();
        NativeSqlReportParser.Result parsed = parser.parse(json);
        int persisted = repository.replaceAll(conn, parsed.rows, runId);
        return new Result(parsed.rows.size(), persisted, parsed.truncated, parsed.columns.size());
    }
}
