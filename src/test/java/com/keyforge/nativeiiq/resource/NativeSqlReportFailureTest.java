package com.keyforge.nativeiiq.resource;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reason a SQL report failed, as sent back to the KF Agent (Gap 2), and the database/SQL file names (Gap 1). */
class NativeSqlReportFailureTest {

    @Test
    void oracleSyntaxErrorNamesStageCodesAndFirstLine() {
        SQLException e = new SQLSyntaxErrorException("ORA-00904: \"X\".\"Y\": invalid identifier\nhttps://docs.oracle.com/error-help", "42000", 904);
        NativeSqlReportFailure f = NativeSqlReportFailure.of("execute", e, NativeSqlReportDialect.ORACLE);

        assertEquals("SQL report failed at stage 'execute': SQLSyntaxErrorException [ORA-00904, SQLState 42000]: "
                + "ORA-00904: \"X\".\"Y\": invalid identifier. Full details are in the IdentityIQ server log.", f.message());
        assertFalse(f.timedOut);
        Map<String, Object> fields = f.fields();
        assertEquals("execute", fields.get("stage"));
        assertEquals("SQLSyntaxErrorException", fields.get("errorType"));
        assertEquals("42000", fields.get("sqlState"));
        assertEquals("ORA-00904", fields.get("errorCode"));
        assertEquals(Boolean.FALSE, fields.get("timedOut"));
    }

    @Test
    void essentialsFitInTheAgentsNormalLogLine() {
        SQLException e = new SQLSyntaxErrorException("ORA-00942: table or view does not exist", "42000", 942);
        String message = NativeSqlReportFailure.of("execute", e, NativeSqlReportDialect.ORACLE).message();
        // The agent keeps the first 200 characters of IIQ's message at normal level.
        assertTrue(message.substring(0, Math.min(200, message.length())).contains("ORA-00942: table or view does not exist"), message);
    }

    @Test
    void timeoutIsRecognisedForJdbcPostgresAndOracle() {
        assertTrue(NativeSqlReportFailure.of("execute",
                new SQLTimeoutException("timed out"), NativeSqlReportDialect.POSTGRESQL).timedOut);
        assertTrue(NativeSqlReportFailure.of("execute",
                new SQLException("ERROR: canceling statement due to user request", "57014"),
                NativeSqlReportDialect.POSTGRESQL).timedOut);
        NativeSqlReportFailure oracle = NativeSqlReportFailure.of("execute",
                new SQLException("ORA-01013: user requested cancel of current operation", "72000", 1013),
                NativeSqlReportDialect.ORACLE);
        assertTrue(oracle.timedOut);
        assertTrue(oracle.message().startsWith("SQL report failed at stage 'execute': query timed out after "
                + NativeSqlReportExecutor.QUERY_TIMEOUT_SECONDS + " s; SQLException [ORA-01013, SQLState 72000]"),
                oracle.message());
    }

    @Test
    void findsTheSqlExceptionInsideAWrapper() {
        RuntimeException wrapped = new RuntimeException("wrapper",
                new IllegalStateException("middle", new SQLException("ERROR: relation \"spt_x\" does not exist", "42P01")));
        NativeSqlReportFailure f = NativeSqlReportFailure.of("execute", wrapped, NativeSqlReportDialect.POSTGRESQL);
        assertEquals("SQLException", f.errorType);
        assertEquals("42P01", f.sqlState);
        assertNull(f.vendorCode());
        assertTrue(f.message().contains("[SQLState 42P01]: ERROR: relation \"spt_x\" does not exist"), f.message());
    }

    @Test
    void unsupportedDatabaseBeforeDetectionUsesTheRootCause() {
        IllegalArgumentException e = new IllegalArgumentException("Unsupported IdentityIQ database product: Microsoft SQL Server");
        NativeSqlReportFailure f = NativeSqlReportFailure.of("getDatabaseProductName", e, null);
        assertEquals("SQL report failed at stage 'getDatabaseProductName': IllegalArgumentException: "
                + "Unsupported IdentityIQ database product: Microsoft SQL Server. Full details are in the IdentityIQ server log.",
                f.message());
        assertNull(f.sqlState);
        assertNull(f.vendorCode());
    }

    @Test
    void messageIsOneSafeBoundedLine() {
        StringBuilder longText = new StringBuilder("first\tline ");
        for (int i = 0; i < 100; i++) {
            longText.append("0123456789");
        }
        NativeSqlReportFailure f = NativeSqlReportFailure.of("execute",
                new SQLException(longText + "\nsecond line"), NativeSqlReportDialect.POSTGRESQL);
        assertFalse(f.message().contains("\n"));
        assertFalse(f.message().contains("second line"));
        assertTrue(f.detail.length() <= NativeSqlReportFailure.MAX_DETAIL + 3);
    }

    @Test
    void nullOrMessagelessErrorsStillProduceAMessage() {
        assertEquals("SQL report failed at stage 'execute': NullPointerException. Full details are in the IdentityIQ server log.",
                NativeSqlReportFailure.of("execute", new NullPointerException(), NativeSqlReportDialect.ORACLE).message());
        assertEquals("SQL report failed at stage 'start': UnknownError. Full details are in the IdentityIQ server log.",
                NativeSqlReportFailure.of("start", null, null).message());
    }

    @Test
    void databaseDisplayNamesAndSqlFileNames() {
        assertEquals("Oracle", NativeSqlReportDialect.ORACLE.displayName());
        assertEquals("PostgreSQL", NativeSqlReportDialect.POSTGRESQL.displayName());
        assertEquals("entitlementAssignment.oracle.sql", NativeSqlReportResources.fileName(
                NativeSqlReportResources.resourcePathFor("entitlementAssignment", NativeSqlReportDialect.ORACLE)));
        assertEquals("workgroupMembers.sql", NativeSqlReportResources.fileName(
                NativeSqlReportResources.resourcePathFor("workgroupMembers", NativeSqlReportDialect.POSTGRESQL)));
        assertNull(NativeSqlReportResources.fileName(null));
    }
}
