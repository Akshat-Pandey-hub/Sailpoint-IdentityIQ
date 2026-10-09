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
 * {@code start}/{@code limit}, {@code start} defaults to 0, {@code limit} defaults to all, an empty
 * result is {@code []}. No {@code modifiedAfter}: the underlying report client runs the whole SELECT and
 * supports no change-bound, so that param stays reserved/ignored.
 */
public final class NativeSqlReportRestService {

    /** Transport seam: {@code NativeSqlReportClient::fetch} in production, a fake in tests. */
    @FunctionalInterface
    public interface ReportSource {
        /** @return the raw JSON envelope body for the given server-registered report id */
        String fetchReport(String reportId);
    }

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
     * Fetch → parse → filter → window → serialize for one registered report.
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

        NativeSqlReportParser.Result result = parser.parse(source.fetchReport(reportId));

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

        List<Map<String, String>> matched = new ArrayList<>();
        for (Map<String, String> row : result.rows) {
            if (matches(row, filters)) {
                matched.add(row);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (Map<String, String> row : matched.subList(from, to)) {
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
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> f : filters.entrySet()) {
            String actual = row.get(f.getKey());
            if (actual == null || !actual.equals(f.getValue())) { // null never equals a requested value
                return false;
            }
        }
        return true;
    }
}
