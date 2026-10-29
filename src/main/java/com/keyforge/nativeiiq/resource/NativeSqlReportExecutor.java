package com.keyforge.nativeiiq.resource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC-only executor for the plugin SQL reports; kept independent of IIQ classes for focused tests. */
final class NativeSqlReportExecutor {

    static final int MAX_ROWS = 200000;
    static final int QUERY_TIMEOUT_SECONDS = 50;
    static final int FETCH_SIZE = 500;

    private NativeSqlReportExecutor() { }

    static final class BadRequest extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        BadRequest(String message) { super(message); }
    }

    static Map<String, Object> execute(Connection conn, String report, String sql,
                                       Map<String, String> filters, Integer requestedStart,
                                       Integer requestedLimit, Long modifiedAfter) throws Exception {
        return execute(conn, report, sql, filters, requestedStart, requestedLimit, modifiedAfter, null);
    }

    static Map<String, Object> execute(Connection conn, String report, String sql,
                                       Map<String, String> filters, Integer requestedStart,
                                       Integer requestedLimit, Long modifiedAfter,
                                       org.apache.commons.logging.Log log) throws Exception {
        validatePage(requestedStart, requestedLimit);
        int start = requestedStart == null ? 0 : requestedStart.intValue();
        int pageLimit = requestedLimit == null ? MAX_ROWS : requestedLimit.intValue();
        List<String> columns = new ArrayList<String>();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        boolean truncated = false;
        boolean hasMore = false;

        PreparedStatement st = null;
        ResultSet rs = null;
        try {
            st = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            st.setFetchSize(FETCH_SIZE);
            // Counted from our own bundled SQL, not asked of the JDBC driver (getParameterMetaData support varies).
            int parameterCount = NativeSqlReportResources.placeholderCount(sql);
            for (int parameter = 1; parameter <= parameterCount; parameter++) {
                if (modifiedAfter == null) {
                    st.setNull(parameter, Types.BIGINT);
                } else {
                    st.setLong(parameter, modifiedAfter.longValue());
                }
            }
            int maxRows = jdbcMaxRows(start, pageLimit, filters);
            if (log != null && log.isDebugEnabled()) {
                log.debug("KeyForgeNativeIIQ sqlReport[" + report + "]: boundMillis="
                        + (modifiedAfter == null ? "<not set>" : modifiedAfter)
                        + ", start=" + start + ", limit=" + pageLimit + ", maxRows=" + maxRows);
            }
            if (maxRows > 0) {
                st.setMaxRows(maxRows);
            }
            rs = st.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int count = md.getColumnCount();
            for (int c = 1; c <= count; c++) {
                columns.add(md.getColumnLabel(c));
            }
            if (filters != null) {
                for (String key : filters.keySet()) {
                    if (!columns.contains(key)) {
                        throw new BadRequest("unknown filter field '" + key + "'");
                    }
                }
            }

            int matched = 0;
            while (rs.next()) {
                Map<String, Object> row = null;
                if (filters != null && !filters.isEmpty()) {
                    row = readRow(rs, columns, count);
                    if (!matches(row, filters)) {
                        continue;
                    }
                } else if (matched < start) {
                    matched++;
                    continue;
                }

                if (matched < start) {
                    matched++;
                    continue;
                }
                if (rows.size() >= pageLimit) {
                    hasMore = true;
                    truncated = requestedLimit == null;
                    break;
                }
                if (row == null) {
                    row = readRow(rs, columns, count);
                }
                rows.add(row);
                matched++;
            }
            if (log != null && log.isDebugEnabled()) {
                log.debug("KeyForgeNativeIIQ sqlReport[" + report + "]: rowsSkipped="
                        + Math.min(matched, start) + ", returned=" + rows.size() + ", hasMore=" + hasMore);
            }
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (Exception ignore) { }
            }
            if (st != null) {
                try { st.close(); } catch (Exception ignore) { }
            }
            // The Connection belongs to SailPointContext and must remain open.
        }

        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("report", report);
        envelope.put("columns", columns);
        envelope.put("rows", rows);
        envelope.put("returned", Integer.valueOf(rows.size()));
        envelope.put("truncated", Boolean.valueOf(truncated));
        envelope.put("hasMore", Boolean.valueOf(hasMore));
        envelope.put("pagingApplied", Boolean.TRUE);
        return envelope;
    }

    private static Map<String, Object> readRow(ResultSet rs, List<String> columns, int count) throws Exception {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int c = 1; c <= count; c++) {
            row.put(columns.get(c - 1), rs.getString(c));
        }
        return row;
    }

    private static boolean matches(Map<String, Object> row, Map<String, String> filters) {
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            Object value = row.get(filter.getKey());
            if (!(value instanceof String) || !value.equals(filter.getValue())) return false;
        }
        return true;
    }

    private static int jdbcMaxRows(int start, int limit, Map<String, String> filters) {
        if (filters != null && !filters.isEmpty()) return 0;
        long max = (long) start + (long) limit + 1L;
        return max > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) max;
    }

    static Integer parsePageValue(String value, String name) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new BadRequest("invalid '" + name + "': expected a 32-bit integer");
        }
    }

    static void validatePage(Integer start, Integer limit) {
        if (start != null && start.intValue() < 0) {
            throw new BadRequest("invalid 'start': must be zero or greater");
        }
        if (limit != null && (limit.intValue() < 1 || limit.intValue() > MAX_ROWS)) {
            throw new BadRequest("invalid 'limit': must be between 1 and " + MAX_ROWS);
        }
        long endExclusive = (long) (start == null ? 0 : start.intValue())
                + (long) (limit == null ? MAX_ROWS : limit.intValue()) + 1L;
        if (endExclusive > Integer.MAX_VALUE) {
            throw new BadRequest("invalid paging range: start plus limit exceeds JDBC's supported row range");
        }
    }
}
