package com.keyforge.nativeiiq.resource;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        boolean failOnExecute;
        boolean failOnBind;
        boolean failOnPrepare;

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
                    case "setLong":
                        if (failOnBind) throw new java.sql.SQLException("synthetic bind failure");
                        parameters.put((Integer) args[0], args[1]); parameterTypes.put((Integer) args[0], Types.BIGINT); return null;
                    case "setNull":
                        if (failOnBind) throw new java.sql.SQLException("synthetic bind failure");
                        parameters.put((Integer) args[0], null); parameterTypes.put((Integer) args[0], (Integer) args[1]); return null;
                    case "executeQuery":
                        if (failOnExecute) throw new java.sql.SQLException("synthetic execute failure");
                        return resultSet();
                    case "close": statementClosed = true; return null;
                    default: return defaultValue(method.getReturnType());
                }
            });
            return proxy(Connection.class, (p, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    if (failOnPrepare) throw new java.sql.SQLException("synthetic prepare failure");
                    return statement;
                }
                return defaultValue(method.getReturnType());
            });
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
        List<String> complete = new ArrayList<String>(ids(page1));
        complete.addAll(ids(page2));
        assertEquals(Arrays.asList("a", "b", "c", "d"), complete);
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
    void closesJdbcResourcesWhenSqlExecutionFails() {
        JdbcFixture fixture = fixture();
        fixture.failOnExecute = true;
        assertThrows(Exception.class, () -> execute(fixture, null, 0, 1));
        assertTrue(fixture.statementClosed);
        assertFalse(fixture.resultSetClosed);
    }

    @Test
    void closesJdbcResourcesWhenParameterBindingFails() {
        JdbcFixture fixture = fixture();
        fixture.parameterCount = 1;
        fixture.failOnBind = true;
        assertThrows(Exception.class, () -> NativeSqlReportExecutor.execute(
                fixture.connection(), "test", "SELECT ?", null, 0, 1, Long.valueOf(1L)));
        assertTrue(fixture.statementClosed);
        assertFalse(fixture.resultSetClosed);
    }

    @Test
    void prepareFailureDoesNotRequireClosingUncreatedStatement() {
        JdbcFixture fixture = fixture();
        fixture.failOnPrepare = true;
        assertThrows(Exception.class, () -> execute(fixture, null, 0, 1));
        assertFalse(fixture.statementClosed);
        assertFalse(fixture.resultSetClosed);
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

    @Test
    void everyRegisteredSqlVariantHasExpectedPlaceholdersAndBindsNullOrMillisInPositionOrder() throws Exception {
        String[] reports = {"entitlementAssignment", "workgroupMembers",
                "entitlementCertification", "entitlementCertificationStatus"};
        NativeSqlReportDialect[] dialects = {NativeSqlReportDialect.POSTGRESQL, NativeSqlReportDialect.ORACLE};
        int[] expectedCounts = {4, 2, 6, 10};
        long bound = 1791801000123L;

        for (NativeSqlReportDialect dialect : dialects) {
            for (int r = 0; r < reports.length; r++) {
                String path = NativeSqlReportResources.resourcePathFor(reports[r], dialect);
                String sql = NativeSqlReportResources.loadSql(path);
                int actualCount = countPlaceholders(sql);
                assertEquals(expectedCounts[r], actualCount, reports[r] + " / " + dialect);

                JdbcFixture nullFixture = fixture();
                nullFixture.parameterCount = actualCount;
                NativeSqlReportExecutor.execute(nullFixture.connection(), reports[r], sql,
                        null, 0, 2, null);
                assertEquals(actualCount, nullFixture.parameters.size(), reports[r] + " null binds");
                for (int p = 1; p <= actualCount; p++) {
                    assertTrue(nullFixture.parameters.containsKey(p));
                    assertEquals(null, nullFixture.parameters.get(p));
                    assertEquals(Integer.valueOf(Types.BIGINT), nullFixture.parameterTypes.get(p));
                }

                JdbcFixture valueFixture = fixture();
                valueFixture.parameterCount = actualCount;
                NativeSqlReportExecutor.execute(valueFixture.connection(), reports[r], sql,
                        null, 0, 2, Long.valueOf(bound));
                assertEquals(actualCount, valueFixture.parameters.size(), reports[r] + " value binds");
                for (int p = 1; p <= actualCount; p++) {
                    assertEquals(Long.valueOf(bound), valueFixture.parameters.get(p));
                    assertEquals(Integer.valueOf(Types.BIGINT), valueFixture.parameterTypes.get(p));
                }
            }
        }
    }

    @Test
    void dialectResourceAllowlistPreservesPostgresPathsAndLoadsBothVariants() throws Exception {
        String[] reports = {"entitlementAssignment", "workgroupMembers",
                "entitlementCertification", "entitlementCertificationStatus"};
        for (String report : reports) {
            String postgres = NativeSqlReportResources.resourcePathFor(report, NativeSqlReportDialect.POSTGRESQL);
            String oracle = NativeSqlReportResources.resourcePathFor(report, NativeSqlReportDialect.ORACLE);
            assertEquals("/com/keyforge/nativeiiq/sql/" + report + ".sql", postgres);
            assertEquals("/com/keyforge/nativeiiq/sql/" + report + ".oracle.sql", oracle);
            assertTrue(NativeSqlReportResources.isSingleSelect(NativeSqlReportResources.loadSql(postgres)));
            assertTrue(NativeSqlReportResources.isSingleSelect(NativeSqlReportResources.loadSql(oracle)));
        }
        assertEquals(null, NativeSqlReportResources.resourcePathFor("arbitrary", NativeSqlReportDialect.ORACLE));
    }

    @Test
    void oracleIncrementalGreatestDoesNotTreatAllNullDatesAsNewForPreEpochBounds() throws Exception {
        String[] reports = {"entitlementAssignment", "workgroupMembers",
                "entitlementCertification", "entitlementCertificationStatus"};
        for (String report : reports) {
            String sql = NativeSqlReportResources.loadSql(
                    NativeSqlReportResources.resourcePathFor(report, NativeSqlReportDialect.ORACLE));
            assertTrue(sql.contains("-9223372036854775808"), report);
        }
    }

    @Test
    void oracleProjectionAliasesRemainInTheExistingReportOrder() throws Exception {
        Map<String, List<String>> expected = new LinkedHashMap<String, List<String>>();
        expected.put("entitlementAssignment", Arrays.asList("identity_id", "user_id", "user_name",
                "application_name", "account_name", "entitlement_attribute", "entitlement_value",
                "assigned", "granted_by_role", "entitlement_source", "entitlement_assignment_id",
                "entitlement_assigner", "entitlement_created", "entitlement_modified", "role_id",
                "role_name", "role_display_name", "role_type", "role_requestable", "identity_request_id",
                "access_request_id", "request_type", "requested_by", "request_date", "request_status",
                "request_operation", "provisioning_state", "assignment_type", "assignment_reason"));
        expected.put("workgroupMembers", Arrays.asList("workgroup_name", "user_name", "display_name",
                "first_name", "last_name", "email", "user_status"));
        expected.put("entitlementCertification", Arrays.asList("user_id", "user_name", "application_name",
                "account_name", "entitlement_attribute", "entitlement_value", "entitlement_assigned_date",
                "entitlement_modified_date", "never_certified"));
        expected.put("entitlementCertificationStatus", Arrays.asList("user_id", "user_name", "manager_user_id",
                "manager_name", "application_name", "account_name", "entitlement_attribute",
                "entitlement_value", "entitlement_assigned_date", "last_certification_name",
                "last_certified_date", "certifier", "certification_decision", "never_certified"));

        Pattern aliasPattern = Pattern.compile("\\bAS\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        for (Map.Entry<String, List<String>> report : expected.entrySet()) {
            String sql = NativeSqlReportResources.loadSql(NativeSqlReportResources.resourcePathFor(
                    report.getKey(), NativeSqlReportDialect.ORACLE));
            Matcher matcher = aliasPattern.matcher(sql);
            List<String> aliases = new ArrayList<String>();
            while (matcher.find()) aliases.add(matcher.group(1));
            assertEquals(report.getValue(), aliases, report.getKey());
        }
    }

    @Test
    void resourceLoadingFailureIsExplicit() {
        assertThrows(IllegalStateException.class,
                () -> NativeSqlReportResources.loadSql("/missing/report.sql"));
    }

    @Test
    void unsupportedOrMissingDatabaseProductsFailInsteadOfFallingBack() {
        assertEquals(NativeSqlReportDialect.POSTGRESQL,
                NativeSqlReportDialect.fromProductName(" PostgreSQL "));
        assertEquals(NativeSqlReportDialect.ORACLE, NativeSqlReportDialect.fromProductName("Oracle"));
        assertEquals(NativeSqlReportDialect.ORACLE, NativeSqlReportDialect.fromProductName("Oracle Database"));
        assertThrows(IllegalArgumentException.class, () -> NativeSqlReportDialect.fromProductName(null));
        assertThrows(IllegalArgumentException.class, () -> NativeSqlReportDialect.fromProductName("  "));
        assertThrows(IllegalArgumentException.class, () -> NativeSqlReportDialect.fromProductName("MySQL"));
        assertThrows(IllegalArgumentException.class, () -> NativeSqlReportDialect.fromProductName("EnterpriseDB"));
    }

    @Test
    void metadataLookupUsesTheSuppliedConnectionAndPropagatesFailure() throws Exception {
        Connection connection = proxy(Connection.class, (p, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return proxy(DatabaseMetaData.class, (m, metadataMethod, metadataArgs) ->
                        "getDatabaseProductName".equals(metadataMethod.getName())
                                ? "Oracle" : defaultValue(metadataMethod.getReturnType()));
            }
            return defaultValue(method.getReturnType());
        });
        assertEquals(NativeSqlReportDialect.ORACLE, NativeSqlReportDialect.fromConnection(connection));

        Connection blankProduct = proxy(Connection.class, (p, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return proxy(DatabaseMetaData.class, (m, metadataMethod, metadataArgs) ->
                        "getDatabaseProductName".equals(metadataMethod.getName())
                                ? " " : defaultValue(metadataMethod.getReturnType()));
            }
            return defaultValue(method.getReturnType());
        });
        assertThrows(IllegalArgumentException.class,
                () -> NativeSqlReportDialect.fromConnection(blankProduct));

        Connection failing = proxy(Connection.class, (p, method, args) -> {
            if ("getMetaData".equals(method.getName())) throw new java.sql.SQLException("metadata unavailable");
            return defaultValue(method.getReturnType());
        });
        assertThrows(java.sql.SQLException.class, () -> NativeSqlReportDialect.fromConnection(failing));

        Connection noMetadata = proxy(Connection.class, (p, method, args) ->
                "getMetaData".equals(method.getName()) ? null : defaultValue(method.getReturnType()));
        assertThrows(IllegalStateException.class, () -> NativeSqlReportDialect.fromConnection(noMetadata));
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !doubleQuoted) {
                if (singleQuoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    singleQuoted = !singleQuoted;
                }
            } else if (c == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (c == '?' && !singleQuoted && !doubleQuoted) {
                count++;
            }
        }
        return count;
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
