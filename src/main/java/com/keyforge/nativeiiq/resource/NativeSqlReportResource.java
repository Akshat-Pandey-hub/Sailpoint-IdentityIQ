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

        // workgroupMembers: business Query 2 (workgroups and their members' status), verbatim, with three
        // and only three schema/type corrections authoritatively grounded in Identity.hbm.xml (inside
        // lib/identityiq.jar): (a) the membership link table spt_identity_workgroups has physical columns
        // identity_id (key), workgroup (many-to-many FK to the workgroup Identity) and idx -- there is NO
        // `workgroup_id` -- so the join `wg.id = iw.workgroup_id` became `wg.id = iw.workgroup`; plus the
        // two boolean adaptations below. NOTE two unrelated columns share the name "workgroup":
        // spt_identity.workgroup (BOOLEAN is-workgroup flag, used by WHERE wg.workgroup = TRUE) and
        // spt_identity_workgroups.workgroup (varchar FK, used by the corrected join) -- distinct columns.
        // PostgreSQL type adaptation only: spt_identity.workgroup and spt_identity.inactive are
        // BOOLEAN columns in IIQ's PostgreSQL schema (confirmed by the native layer's isWorkgroup()/
        // isInactive() and its `is_workgroup boolean`/`inactive boolean` DDL), and PostgreSQL has no
        // implicit boolean<->integer coercion, so the two `= 1` comparisons (WHERE wg.workgroup = 1 and
        // the CASE `u.inactive = 1`) became `= TRUE`. `= TRUE` (not IS TRUE) preserves the original
        // NULL behaviour exactly. No alias, LEFT JOIN, CASE ordering or ORDER BY (incl. u.inactive DESC,
        // valid on a boolean) was otherwise changed. Not an arbitrary-SQL surface: baked, keyed by name.
        REPORTS.put("workgroupMembers", "/com/keyforge/nativeiiq/sql/workgroupMembers.sql");

        // entitlementCertification: business Query 3 (entitlement + never-certified flag), verbatim, with
        // ONE PostgreSQL compatibility correction only, proven from identityiq.jar Hibernate mappings:
        // spt_identity_entitlement.created/.modified map to sailpoint.persistence.DateType, which stores a
        // java.util.Date as a BIGINT of epoch MILLISECONDS (DateType.sqlTypes()=BIGINT, get/setLong). The
        // original MySQL `FROM_UNIXTIME(ie.created / 1000)` has no PostgreSQL equivalent, so both date
        // expressions became `to_timestamp(ie.created / 1000.0)` / `to_timestamp(ie.modified / 1000.0)`:
        // the `/ 1000.0` (numeric) divides epoch-ms to epoch-seconds preserving sub-second precision
        // exactly as MySQL's non-integer `/ 1000` did (a bare `/ 1000` in PostgreSQL would be integer
        // division and truncate milliseconds). Every other name/type/semantic was verified present and
        // compatible (all joins text=text; CertificationItem.exception_application/exception_attribute_name/
        // exception_attribute_value and CertificationEntity.target_id/target_name/native_identity exist;
        // ci.certification_entity_id -> ce.id confirmed), so EXISTS, the target_id/target_name OR, the
        // native_identity NULL fallback, THEN 'NO'/ELSE 'YES', and ORDER BY were left unchanged. Baked,
        // keyed by name: not an arbitrary-SQL surface.
        REPORTS.put("entitlementCertification", "/com/keyforge/nativeiiq/sql/entitlementCertification.sql");

        // entitlementCertificationStatus: business Query 4 (every entitlement + its latest certification
        // info), verbatim, with THREE PostgreSQL compatibility corrections only, all proven from
        // identityiq.jar Hibernate mappings and none changing business meaning:
        //   (a) spt_identity_entitlement.created and spt_certification_action.decision_date both map to
        //       sailpoint.persistence.DateType = BIGINT epoch-MILLISECONDS (DateType.sqlTypes()=BIGINT),
        //       so the MySQL FROM_UNIXTIME(x / 1000) became to_timestamp(x / 1000.0) in both places
        //       (/ 1000.0 numeric preserves sub-second precision; bare / 1000 would integer-truncate ms).
        //   (b) window ORDER BY `ca.decision_date DESC` -> `ca.decision_date DESC NULLS LAST`: MySQL sorts
        //       NULLs last on DESC, PostgreSQL sorts them first by default, so without NULLS LAST an
        //       undecided certification (decision_date NULL) would outrank a decided one for the same
        //       entitlement and win rn=1 -- inverting the intended "latest DECIDED certification". NULLS
        //       LAST makes PostgreSQL reproduce MySQL's ordering exactly. (c.created is never NULL, so its
        //       DESC needs no NULLS clause.) This preserves, not changes, the business semantics.
        // Everything else verified present/compatible and left unchanged: spt_certification(.name,.created),
        // spt_certification_action(.id via ci.action, .decision_date, .actor_name=certifier,
        // .status=CertificationAction$Status decision enum), spt_identity.manager->mgr.id,
        // ce.certification_id->c.id, ci.certification_entity_id->ce.id, the three exception_* matches, the
        // target_id/native_identity-NULL-fallback join, ROW_NUMBER() partition, the CTE, every LEFT JOIN
        // (entitlements with no certification are preserved), 'YES'/'NO', and the final ORDER BY. Baked,
        // keyed by name: not an arbitrary-SQL surface.
        REPORTS.put("entitlementCertificationStatus",
                "/com/keyforge/nativeiiq/sql/entitlementCertificationStatus.sql");
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
