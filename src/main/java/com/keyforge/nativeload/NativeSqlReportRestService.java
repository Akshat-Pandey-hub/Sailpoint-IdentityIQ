package com.keyforge.nativeload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared KF Agent REST read service for the native <b>SQL-report</b> family (Query 1..4). Unlike the
 * native-entity services, a SQL report has no typed record: the IIQ plugin executes a <b>baked,
 * server-side, read-only</b> business {@code SELECT} against IIQ's own database (via
 * {@code SailPointContext.getJdbcConnection()}) and returns {@code {report, columns, rows[], ...}} with
 * every value as text and SQL {@code NULL} preserved as JSON {@code null}. This service transports that
 * envelope out over the established authenticated session — it reuses {@link NativeSqlReportClient} and
 * {@link NativeSqlReportParser}, never opens a PostgreSQL connection, and never touches the DB
 * import/extraction path. The existing {@code extract-native-*-db} commands are unaffected.
 *
 * <p><b>Generic over columns by design.</b> The service hardcodes no field list: the authoritative
 * column contract is the {@code columns} array the plugin returns at runtime (the query's SELECT
 * aliases, in order). Every returned column is a genuine business/report field (a SailPoint SELECT
 * alias), so all are exposed and all are scalar-filterable; there is no KeyForge persistence metadata
 * to exclude (the DB primary key, record hash and lineage columns live only in the {@code kf_*} table
 * the <i>import</i> path writes, never in the report result). Rows keep the column order and preserve
 * nulls.
 *
 * <p><b>Not an arbitrary-SQL surface.</b> The caller never supplies SQL or a report name: each KF Agent
 * route binds a fixed, server-registered report id. As defense in depth this service also refuses any
 * {@code reportId} that is not in its registered allow-list.
 *
 * <p>Filtering/paging follow the established REST contract: exact scalar-column equality, multiple
 * filters AND-combined, an unknown filter name (not a runtime column) throws
 * {@link IllegalArgumentException} (the routing layer maps it to HTTP 400), filtering is applied before
 * {@code start}/{@code limit}, and {@code start} defaults to 0. When omitted, {@code limit} preserves the
 * plugin's legacy cap; a capped/incomplete response becomes an error instead of a partial success. An
 * empty page is {@code []}. The agent forwards the reserved {@code modifiedAfter} parameter to the plugin for filtering inside SQL.
 */
public final class NativeSqlReportRestService {

    /** Transport seam: {@code NativeSqlReportClient::fetch} in production, a fake in tests. */
    @FunctionalInterface
    public interface ReportSource {
        /** @return the raw JSON envelope body for the given server-registered report id */
        String fetchReport(String reportId);

        /** Existing test/fake sources may ignore paging; production supplies the complete request. */
        default String fetchReport(String reportId, Map<String, String> filters,
                                   Integer start, Integer limit) {
            return fetchReport(reportId);
        }
    }

    private static final int MAX_PAGE_SIZE = 200000;

    private final NativeSqlReportParser parser = new NativeSqlReportParser();

    /** Server-registered report ids this service is allowed to request (defense in depth). */
    private final Set<String> registeredReports;

    public NativeSqlReportRestService(Set<String> registeredReports) {
        this.registeredReports =
                Collections.unmodifiableSet(new LinkedHashSet<>(registeredReports));
    }

    public Set<String> registeredReports() {
        return registeredReports;
    }

    /**
     * Fetch one plugin page → parse → serialize. The plugin applies the existing exact-string filters
     * before paging while streaming the JDBC result set, so the agent no longer buffers the full report.
     *
     * @throws IllegalArgumentException if {@code reportId} is not registered, or a filter names a
     *                                  field that is not one of the report's runtime columns
     */
    public List<Map<String, Object>> fetch(ReportSource source, String reportId,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (reportId == null || !registeredReports.contains(reportId)) {
            throw new IllegalArgumentException("unknown report '" + reportId
                    + "'; registered reports are: " + registeredReports);
        }

        int normalizedStart = start == null ? 0 : start.intValue();
        if (normalizedStart < 0) {
            throw new IllegalArgumentException("invalid 'start': must be zero or greater");
        }
        if (limit != null && (limit.intValue() < 1 || limit.intValue() > MAX_PAGE_SIZE)) {
            throw new IllegalArgumentException("invalid 'limit': must be between 1 and " + MAX_PAGE_SIZE);
        }
        long endExclusive = (long) normalizedStart + (long) (limit == null ? MAX_PAGE_SIZE : limit.intValue()) + 1L;
        if (endExclusive > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid paging range: start plus limit exceeds JDBC's supported row range");
        }

        NativeSqlReportParser.Result result = parser.parse(
                source.fetchReport(reportId, filters, start, limit));
        com.keyforge.iiq.debug.DebugLog.log("SQL-REPORT", "report", reportId,
                "database", result.database == null ? "<not reported by plugin>" : result.database,
                "sql", result.sqlFile == null ? "<not reported by plugin>" : result.sqlFile);
        if (result.truncated) {
            throw new NativeImportException("IIQ returned an incomplete SQL report; request a smaller page.");
        }

        // The authoritative, generic column contract is whatever the plugin returned at runtime.
        Set<String> columns = new LinkedHashSet<>(result.columns);
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!columns.contains(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + columns);
                }
            }
        }

        List<Map<String, String>> selected = result.rows;
        if (!result.pagingApplied) {
            // Compatibility with an older plugin that ignores paging/filter query parameters.
            List<Map<String, String>> matched = new ArrayList<>();
            for (Map<String, String> row : result.rows) {
                if (matches(row, filters)) matched.add(row);
            }
            int from = Math.min(normalizedStart, matched.size());
            int to = limit == null ? matched.size()
                    : (int) Math.min((long) matched.size(), (long) from + limit.intValue());
            selected = matched.subList(from, Math.max(from, to));
        }

        List<Map<String, Object>> out = new ArrayList<>(selected.size());
        for (Map<String, String> row : selected) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (!result.columns.isEmpty()) {
                for (String col : result.columns) {
                    m.put(col, row.get(col)); // preserve column order and SQL NULL (as Java null)
                }
            } else {
                m.putAll(row);
            }
            out.add(m);
        }
        return out;
    }

    private static boolean matches(Map<String, String> row, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return true;
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            String actual = row.get(filter.getKey());
            if (actual == null || !actual.equals(filter.getValue())) return false;
        }
        return true;
    }
}
