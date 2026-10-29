package com.keyforge.nativeiiq.resource;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Why a SQL report failed, in a form that is safe to send back to the caller: the stage it failed at, the error type,
 * the database's own SQLState and error code, whether the query timed out, and the first line of the error message.
 * These reports are read-only SELECTs, so a database error describes the query (a table, column or syntax), never row
 * data. The full stack trace is still written to the IdentityIQ server log.
 *
 * <p>The message puts the essentials first, because the KF Agent's normal-level log keeps only the start of it.
 */
final class NativeSqlReportFailure {

    static final int MAX_DETAIL = 200;
    private static final int MAX_CAUSE_DEPTH = 20;

    /** PostgreSQL: query_canceled (statement timeout). */
    private static final String POSTGRES_QUERY_CANCELED = "57014";
    /** Oracle: ORA-01013 user requested cancel of current operation (the JDBC query timeout). */
    private static final int ORACLE_USER_REQUESTED_CANCEL = 1013;

    final String stage;
    final String errorType;
    final String sqlState;
    final Integer errorCode;
    final boolean timedOut;
    final String detail;
    private final NativeSqlReportDialect dialect;
    private final int queryTimeoutSeconds;

    private NativeSqlReportFailure(String stage, String errorType, String sqlState, Integer errorCode,
                                   boolean timedOut, String detail, NativeSqlReportDialect dialect,
                                   int queryTimeoutSeconds) {
        this.stage = stage;
        this.errorType = errorType;
        this.sqlState = sqlState;
        this.errorCode = errorCode;
        this.timedOut = timedOut;
        this.detail = detail;
        this.dialect = dialect;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    /**
     * @param dialect the detected database, or {@code null} when the failure happened before detection
     */
    static NativeSqlReportFailure of(String stage, Throwable t, NativeSqlReportDialect dialect) {
        return of(stage, t, dialect, NativeSqlReportExecutor.QUERY_TIMEOUT_SECONDS);
    }

    /** @param queryTimeoutSeconds the query timeout that was applied, named in the message when the query timed out */
    static NativeSqlReportFailure of(String stage, Throwable t, NativeSqlReportDialect dialect, int queryTimeoutSeconds) {
        SQLException sql = firstSqlException(t);
        Throwable reported = sql != null ? sql : rootCause(t);
        String sqlState = sql == null ? null : blankToNull(sql.getSQLState());
        Integer errorCode = sql == null || sql.getErrorCode() == 0 ? null : Integer.valueOf(sql.getErrorCode());
        boolean timedOut = sql instanceof SQLTimeoutException
                || POSTGRES_QUERY_CANCELED.equals(sqlState)
                || (errorCode != null && errorCode.intValue() == ORACLE_USER_REQUESTED_CANCEL);
        return new NativeSqlReportFailure(stage,
                reported == null ? "UnknownError" : reported.getClass().getSimpleName(),
                sqlState, errorCode, timedOut, firstLine(reported == null ? null : reported.getMessage()), dialect,
                queryTimeoutSeconds);
    }

    /** One line, most important first, e.g. {@code ... stage 'execute': SQLSyntaxErrorException [ORA-00904, SQLState 42000]: ...}. */
    String message() {
        StringBuilder sb = new StringBuilder("SQL report failed at stage '").append(stage).append("': ");
        if (timedOut) {
            sb.append("query timed out after ").append(queryTimeoutSeconds).append(" s; ");
        }
        sb.append(errorType);
        String codes = codes();
        if (codes != null) {
            sb.append(" [").append(codes).append(']');
        }
        if (detail != null) {
            sb.append(": ").append(detail);
        }
        sb.append(". Full details are in the IdentityIQ server log.");
        return sb.toString();
    }

    /** The structured fields added to the error response next to {@code message}. */
    Map<String, Object> fields() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("stage", stage);
        out.put("errorType", errorType);
        out.put("sqlState", sqlState);
        out.put("errorCode", vendorCode());
        out.put("timedOut", Boolean.valueOf(timedOut));
        return out;
    }

    /** The database's own error code, as that database writes it ({@code ORA-00904} on Oracle). */
    String vendorCode() {
        if (errorCode == null) {
            return null;
        }
        if (dialect == NativeSqlReportDialect.ORACLE) {
            return String.format("ORA-%05d", errorCode);
        }
        return errorCode.toString();
    }

    private String codes() {
        String vendor = vendorCode();
        if (vendor == null && sqlState == null) {
            return null;
        }
        if (vendor == null) {
            return "SQLState " + sqlState;
        }
        return sqlState == null ? vendor : vendor + ", SQLState " + sqlState;
    }

    private static SQLException firstSqlException(Throwable t) {
        Throwable c = t;
        for (int depth = 0; c != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (c instanceof SQLException) {
                return (SQLException) c;
            }
            c = c.getCause();
        }
        return null;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        for (int depth = 0; c != null && c.getCause() != null && c.getCause() != c && depth < MAX_CAUSE_DEPTH; depth++) {
            c = c.getCause();
        }
        return c;
    }

    private static String firstLine(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        String line = message.trim();
        int nl = line.indexOf('\n');
        if (nl >= 0) {
            line = line.substring(0, nl).trim();
        }
        line = LogText.safe(line);
        return line.length() > MAX_DETAIL ? line.substring(0, MAX_DETAIL) + "..." : line;
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
