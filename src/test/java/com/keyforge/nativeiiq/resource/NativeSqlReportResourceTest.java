package com.keyforge.nativeiiq.resource;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JDBC-level paging, timeout, and cleanup tests for the IIQ plugin SQL-report executor. */
class NativeSqlReportResourceTest {

    private static final class JdbcFixture {
        final List<String[]> data;
        int rowIndex = -1;
        int timeout;
        int maxRows;
        int parameterCount;
        final Map<Integer, Object> parameters = new LinkedHashMap<Integer, Object>();
        final Map<Integer, Integer> parameterTypes = new LinkedHashMap<Integer, Integer>();
        boolean statementClosed;
        boolean resultSetClosed;
        boolean failOnRead;
        boolean failOnNext;

        JdbcFixture(List<String[]> data) {
            this.data = data;
        }

        Connection connection() {
            PreparedStatement statement = proxy(PreparedStatement.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "setQueryTimeout": timeout = (Integer) args[0]; return null;
                    case "setMaxRows": maxRows = (Integer) args[0]; return null;
                    case "getParameterMetaData": return proxy(ParameterMetaData.class, (mp, mm, ma) ->
                            "getParameterCount".equals(mm.getName()) ? parameterCount : defaultValue(mm.getReturnType()));
                    case "setLong": parameters.put((Integer) args[0], args[1]); parameterTypes.put((Integer) args[0], Types.BIGINT); return null;
                    case "setNull": parameters.put((Integer) args[0], null); parameterTypes.put((Integer) args[0], (Integer) args[1]); return null;
                    case "executeQuery": return resultSet();
                    case "close": statementClosed = true; return null;
                    default: return defaultValue(method.getReturnType());
                }
            });
            return proxy(Connection.class, (p, method, args) ->
                    "prepareStatement".equals(method.getName()) ? statement
                            : defaultValue(method.getReturnType()));
        }

        private ResultSet resultSet() {
            ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (p, method, args) -> {
                if ("getColumnCount".equals(method.getName())) return 2;
                if ("getColumnLabel".equals(method.getName())) {
                    return (Integer) args[0] == 1 ? "user_id" : "department";
                }
                return defaultValue(method.getReturnType());
            });
            return proxy(ResultSet.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "getMetaData": return metadata;
                    case "next":
                        if (failOnNext) throw new java.sql.SQLTimeoutException("synthetic query timeout");
                        rowIndex++;
                        return rowIndex < data.size() && (maxRows == 0 || rowIndex < maxRows);
                    case "getString":
                        if (failOnRead) throw new java.sql.SQLException("synthetic row read failure");
                        return data.get(rowIndex)[(Integer) args[0] - 1];
                    case "close": resultSetClosed = true; return null;
                    default: return defaultValue(method.getReturnType());
                }
            });
        }
    }

    @Test
    void returnsFirstAndLaterPagesAndCapsUnfilteredRowsAtWindowPlusLookahead() throws Exception {
        JdbcFixture first = fixture("a", "b", "c", "d");
        Map<String, Object> page1 = execute(first, null, 0, 2);
        assertEquals(Arrays.asList("a", "b"), ids(page1));
        assertEquals(Boolean.TRUE, page1.get("hasMore"));
        assertEquals(Boolean.FALSE, page1.get("truncated"));
        assertEquals(3, first.maxRows);
        assertEquals(NativeSqlReportExecutor.QUERY_TIMEOUT_SECONDS, first.timeout);

        JdbcFixture later = fixture("a", "b", "c", "d");
        Map<String, Object> page2 = execute(later, null, 2, 2);
        assertEquals(Arrays.asList("c", "d"), ids(page2));
        assertEquals(Boolean.FALSE, page2.get("hasMore"));
        assertEquals(5, later.maxRows);
    }

    @Test
    void bindsEverySqlParameterToBoundEpochMillisecondsAndRetainsPaging() throws Exception {
        JdbcFixture fixture = fixture("a", "b", "c", "d");
        fixture.parameterCount = 4;
        long bound = 1791801000123L;
        Map<String, Object> page = NativeSqlReportExecutor.execute(
                fixture.connection(), "test", "SELECT ?, ?, ?, ?", null, 0, 2, Long.valueOf(bound));
        assertEquals(4, fixture.parameters.size());
        for (int i = 1; i <= 4; i++) {
            assertEquals(Long.valueOf(bound), fixture.parameters.get(i));
            assertEquals(Integer.valueOf(Types.BIGINT), fixture.parameterTypes.get(i));
        }
        assertEquals(Arrays.asList("a", "b"), ids(page));
        assertEquals(Boolean.TRUE, page.get("hasMore"));
        assertEquals(3, fixture.maxRows);
    }

    @Test
    void bindsNullAsBigintForEverySqlParameterWhenBoundIsAbsent() throws Exception {
        JdbcFixture fixture = fixture("a");
        fixture.parameterCount = 3;
        NativeSqlReportExecutor.execute(fixture.connection(), "test", "SELECT ?, ?, ?", null, 0, 1, null);
        assertEquals(3, fixture.parameters.size());
        for (int i = 1; i <= 3; i++) {
            assertTrue(fixture.parameters.containsKey(i));
            assertEquals(null, fixture.parameters.get(i));
            assertEquals(Integer.valueOf(Types.BIGINT), fixture.parameterTypes.get(i));
        }
    }

    @Test
    void filteredPageMatchesBeforeOffsetAndPreservesNullComparison() throws Exception {
        JdbcFixture fixture = new JdbcFixture(Arrays.asList(
                new String[]{"a", "Finance"}, new String[]{"b", "Engineering"},
                new String[]{"c", "Finance"}, new String[]{"d", null},
                new String[]{"e", "Finance"}));
        Map<String, Object> page = execute(fixture, Collections.singletonMap("department", "Finance"), 1, 1);
        assertEquals(Collections.singletonList("c"), ids(page));
        assertEquals(Boolean.TRUE, page.get("hasMore"));
        assertEquals(0, fixture.maxRows, "no JDBC max-row cap before Java-side exact-string filtering");
    }

    @Test
    void emptyAndPastEndPagesAreCompleteAndNotTruncated() throws Exception {
        assertEquals(Collections.emptyList(), ids(execute(fixture("a", "b"), null, 4, 2)));
        Map<String, Object> empty = execute(fixture(), null, 0, 2);
        assertEquals(Collections.emptyList(), ids(empty));
        assertEquals(Boolean.FALSE, empty.get("truncated"));
    }

    @Test
    void legacyUnpagedRequestReportsHardCapTruncation() throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i <= NativeSqlReportExecutor.MAX_ROWS; i++) {
            rows.add(new String[]{"id-" + i, "dept"});
        }
        JdbcFixture fixture = new JdbcFixture(rows);
        Map<String, Object> result = execute(fixture, null, null, null);
        assertEquals(NativeSqlReportExecutor.MAX_ROWS, ((List<?>) result.get("rows")).size());
        assertEquals(Boolean.TRUE, result.get("truncated"));
        assertEquals(Boolean.TRUE, result.get("hasMore"));
        assertEquals(NativeSqlReportExecutor.MAX_ROWS + 1, fixture.maxRows);
    }

    @Test
    void rejectsInvalidAndExcessivePageValues() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeSqlReportExecutor.validatePage(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> NativeSqlReportExecutor.validatePage(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> NativeSqlReportExecutor.validatePage(0, NativeSqlReportExecutor.MAX_ROWS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> NativeSqlReportExecutor.validatePage(0, Integer.MAX_VALUE));
    }

    @Test
    void closesJdbcResourcesWhenRowProjectionFails() {
        JdbcFixture fixture = fixture("a");
        fixture.failOnRead = true;
        assertThrows(Exception.class, () -> execute(fixture, null, 0, 1));
        assertTrue(fixture.resultSetClosed);
        assertTrue(fixture.statementClosed);
    }

    @Test
    void queryTimeoutFailureClosesJdbcResources() {
        JdbcFixture fixture = fixture("a");
        fixture.failOnNext = true;
        assertThrows(Exception.class, () -> execute(fixture, null, 0, 1));
        assertTrue(fixture.resultSetClosed);
        assertTrue(fixture.statementClosed);
    }

    @Test
    void closesJdbcResourcesWhenQueryExecutionFails() {
        JdbcFixture fixture = fixture("a");
        // A failure in setQueryTimeout must still close the statement even before a ResultSet exists.
        Connection conn = proxy(Connection.class, (p, method, args) -> {
            if (!"prepareStatement".equals(method.getName())) return defaultValue(method.getReturnType());
            return proxy(PreparedStatement.class, (sp, sm, sa) -> {
                if ("setQueryTimeout".equals(sm.getName())) throw new java.sql.SQLException("timeout setup failed");
                if ("close".equals(sm.getName())) { fixture.statementClosed = true; return null; }
                return defaultValue(sm.getReturnType());
            });
        });
        assertThrows(Exception.class,
                () -> NativeSqlReportExecutor.execute(conn, "r", "SELECT 1", null, 0, 1, null));
        assertTrue(fixture.statementClosed);
        assertFalse(fixture.resultSetClosed);
    }

    private static JdbcFixture fixture(String... ids) {
        List<String[]> rows = new ArrayList<>();
        for (String id : ids) rows.add(new String[]{id, "dept"});
        return new JdbcFixture(rows);
    }

    private static Map<String, Object> execute(JdbcFixture fixture, Map<String, String> filters,
                                                Integer start, Integer limit) throws Exception {
        return NativeSqlReportExecutor.execute(fixture.connection(), "test", "SELECT test", filters, start, limit, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return 0d;
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Map<String, Object> envelope) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) envelope.get("rows");
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) ids.add((String) row.get("user_id"));
        return ids;
    }
}
