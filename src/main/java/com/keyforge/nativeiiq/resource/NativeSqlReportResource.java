package com.keyforge.nativeiiq.resource;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.SystemAdmin;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plugin REST resource that executes a <b>business-authored SQL report</b> against the IdentityIQ
 * database and returns its result set as JSON. This is the native execution path for the "run the
 * business SELECT at SailPoint and return the rows" requirement: the query runs <i>inside</i> IIQ via
 * {@link SailPointContext#getJdbcConnection()} (the live IIQ application-database connection), so it
 * reads SailPoint's own {@code spt_*} tables directly — no translation to the KeyForge schema.
 *
 * <p><b>The SQL is baked server-side</b> (loaded from a bundled {@code .sql} resource, keyed by report
 * name) and is <b>never</b> accepted from the HTTP caller — the endpoint is therefore not an arbitrary
 * SQL-execution surface. The only normalization applied to the file text is stripping a trailing
 * {@code ;}/whitespace (JDBC executes a single statement); no SQL logic is altered.
 *
 * <p><b>Strictly read-only.</b> It refuses any statement that is not a single {@code SELECT}; it never
 * commits or mutates IIQ. Access is restricted to {@code SystemAdministrator}. The JDBC connection is
 * owned by the context and is <b>not</b> closed here (only the {@link Statement}/{@link ResultSet} are).
 *
 * <p>Full URL: {@code plugin/rest/keyForgeNativeIIQ/sqlReport/{report}} (e.g. {@code entitlementAssignment}).
 */
@Path("keyForgeNativeIIQ")
public class NativeSqlReportResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeSqlReportResource.class.getName());

    /** Hard cap so a single request can never materialize an unbounded result set into memory. */
    static final int MAX_ROWS = 200000;

    /** Report name -> bundled SQL resource path (on the plugin classpath). Baked, never client-supplied. */
    private static final Map<String, String> REPORTS = new LinkedHashMap<String, String>();
    static {
        // entitlementAssignment: business Query 1, verbatim, with exactly one authorized change:
        // the original `b.requestable AS role_requestable` was replaced with `NULL AS role_requestable`.
        // Reason: role_requestable is NULL because the original SQL references a role-level
        // spt_bundle.requestable field that is not present in this IIQ environment, and the project
        // requirements define `requestable` only for entitlements (ManagedAttribute.isRequestable), not
        // roles. No semantically equivalent native role property exists, and RoleTypeDefinition
        // assignability flags are NOT equivalent, so none was substituted.
        //
        // PostgreSQL type adaptation (business semantics unchanged): the IIQ application DB is
        // PostgreSQL, where spt_identity_entitlement.assigned and .granted_by_role are BOOLEAN
        // columns. PostgreSQL has no implicit boolean<->integer coercion, so the original
        // `= 1`/`= 0` comparisons on those two columns were changed to `= TRUE`/`= FALSE`
        // (15 occurrences). `= TRUE`/`= FALSE` (not IS TRUE/IS FALSE) preserves the original
        // three-valued-logic NULL behaviour exactly - every `= 0` is paired with an explicit
        // `OR ... IS NULL`. No other column, join, predicate, CASE branch or alias was changed.
        REPORTS.put("entitlementAssignment", "/com/keyforge/nativeiiq/sql/entitlementAssignment.sql");
    }

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("sqlReport/{report}")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response runReport(@PathParam("report") String report) {
        String stage = "start";
        try {
            String resourcePath = REPORTS.get(report);
            if (resourcePath == null) {
                Map<String, Object> err = new LinkedHashMap<String, Object>();
                err.put("report", report);
                err.put("error", "unknown report (not registered server-side)");
                return Response.status(Response.Status.NOT_FOUND).entity(err).build();
            }

            stage = "loadSql";
            String sql = loadSql(resourcePath);
            if (!isSingleSelect(sql)) {
                throw new IllegalStateException("registered report SQL is not a single read-only SELECT");
            }

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "getJdbcConnection";
            Connection conn = context.getJdbcConnection(); // IIQ application DB; owned by context, not closed here

            stage = "execute";
            Map<String, Object> envelope = execute(conn, report, sql);

            LOG.fine("KeyForgeNativeIIQ sqlReport[" + report + "]: returned "
                    + envelope.get("returned") + " row(s)");
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ sqlReport: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, report, e);
        } catch (Throwable t) {
            // Broader than Exception on purpose: contain Errors so IIQ returns a JSON 500, never exception.jsf.
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ sqlReport: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, report, t);
        }
    }

    /** Runs the SELECT and projects the ResultSet into {columns, rows[], returned, truncated}. */
    private Map<String, Object> execute(Connection conn, String report, String sql) throws Exception {
        List<String> columns = new ArrayList<String>();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        boolean truncated = false;

        Statement st = null;
        ResultSet rs = null;
        try {
            st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            rs = st.executeQuery(sql);
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            for (int c = 1; c <= n; c++) {
                // getColumnLabel honours the SQL alias (AS ...), preserving the business column names.
                columns.add(md.getColumnLabel(c));
            }
            int count = 0;
            while (rs.next()) {
                if (count >= MAX_ROWS) {
                    truncated = true;
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                for (int c = 1; c <= n; c++) {
                    // getString preserves the value verbatim and leaves SQL NULL as JSON null.
                    row.put(columns.get(c - 1), rs.getString(c));
                }
                rows.add(row);
                count++;
            }
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (Exception ignore) { }
            }
            if (st != null) {
                try { st.close(); } catch (Exception ignore) { }
            }
            // Deliberately NOT closing conn: it belongs to the SailPointContext.
        }

        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("report", report);
        env.put("columns", columns);
        env.put("rows", rows);
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("truncated", Boolean.valueOf(truncated));
        return env;
    }

    /** Loads the bundled SQL and strips only a trailing {@code ;}/whitespace (JDBC single-statement). */
    private String loadSql(String resourcePath) throws Exception {
        InputStream in = NativeSqlReportResource.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("bundled SQL resource not found: " + resourcePath);
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            r.close();
        }
        String sql = sb.toString().trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql;
    }

    /** Read-only guard: the text must be a single SELECT (no second statement, no DML/DDL). */
    static boolean isSingleSelect(String sql) {
        if (sql == null) {
            return false;
        }
        String s = sql.trim();
        if (s.isEmpty()) {
            return false;
        }
        String upper = s.toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            return false;
        }
        // No embedded second statement (a lone trailing ; was already stripped before this check).
        if (s.indexOf(';') >= 0) {
            return false;
        }
        return true;
    }

    private Response error(Response.Status status, String report, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("report", report);
        env.put("status", Integer.valueOf(status.getStatusCode()));
        env.put("error", error);
        return Response.status(status).entity(env).build();
    }
}
