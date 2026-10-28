package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.SystemAdmin;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final Log LOG = LogFactory.getLog(NativeSqlReportResource.class);

    // Dialect variants remain fixed server-side; neither report names nor resource paths come from callers.

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("sqlReport/{report}")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response runReport(@PathParam("report") String report, @QueryParam("modifiedAfter") String modifiedAfter,
                              @Context UriInfo uriInfo) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "sqlReport/" + report, uriInfo.getQueryParameters().getFirst("start"), uriInfo.getQueryParameters().getFirst("limit"), modifiedAfter);
        String stage = "start";
try {
            if (!NativeSqlReportResources.isRegistered(report)) {
                Map<String, Object> err = new LinkedHashMap<String, Object>();
                err.put("report", report);
                err.put("error", "unknown report (not registered server-side)");
                return Response.status(Response.Status.NOT_FOUND).entity(err).build();
            }

            if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
                return error(Response.Status.BAD_REQUEST, report,
                        com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp().getMessage());
            }
            java.util.Date parsedModifiedAfter =
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
            Long modifiedAfterMillis = parsedModifiedAfter == null ? null : Long.valueOf(parsedModifiedAfter.getTime());

            Map<String, String> filters = new LinkedHashMap<String, String>();
            MultivaluedMap<String, String> query = uriInfo.getQueryParameters();
            for (Map.Entry<String, List<String>> entry : query.entrySet()) {
                String key = entry.getKey();
                if ("start".equals(key) || "limit".equals(key) || "modifiedAfter".equals(key)) {
                    continue;
                }
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    filters.put(key, values.get(0));
                }
            }
            Integer start = NativeSqlReportExecutor.parsePageValue(query.getFirst("start"), "start");
            Integer limit = NativeSqlReportExecutor.parsePageValue(query.getFirst("limit"), "limit");
            NativeSqlReportExecutor.validatePage(start, limit);

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "getJdbcConnection";
            Connection conn = context.getJdbcConnection(); // IIQ application DB; owned by context, not closed here

            stage = "getDatabaseProductName";
            NativeSqlReportDialect dialect = NativeSqlReportDialect.fromConnection(conn);

            stage = "loadSql";
            String sql = NativeSqlReportResources.loadSql(
                    NativeSqlReportResources.resourcePathFor(report, dialect));
            if (!NativeSqlReportResources.isSingleSelect(sql)) {
                throw new IllegalStateException("registered report SQL is not a single read-only SELECT");
            }

            stage = "execute";
            Map<String, Object> envelope = NativeSqlReportExecutor.execute(
                    conn, report, sql, filters, start, limit, modifiedAfterMillis, LOG);

            PluginLog.result(LOG, "sqlReport/" + report, envelope.get("returned"), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "sqlReport/" + report, stage, e);
            return error(Response.Status.FORBIDDEN, report, "Access denied.");
        } catch (NativeSqlReportExecutor.BadRequest e) {
            return error(Response.Status.BAD_REQUEST, report, e.getMessage());
        } catch (Throwable t) {
            // Broader than Exception on purpose: contain Errors so IIQ returns a JSON 500, never exception.jsf.
            PluginLog.failed(LOG, "sqlReport/" + report, stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, report,
                    "SQL report could not be completed. Check the IdentityIQ server log.");
        }
    }

    private Response error(Response.Status status, String report, String message) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "sqlReport/" + report, message);
        }
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("message", message);
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("report", report);
        env.put("status", Integer.valueOf(status.getStatusCode()));
        env.put("error", error);
        return Response.status(status).entity(env).build();
    }
}
