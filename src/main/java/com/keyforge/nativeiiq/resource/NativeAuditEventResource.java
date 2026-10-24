package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeAuditEventExtractionResult;
import com.keyforge.nativeiiq.service.NativeAuditEventExtractionService;
import com.keyforge.nativeiiq.wire.NativeAuditEventWire;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.SystemAdmin;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Map;

/**
 * Plugin REST resource exporting native {@code AuditEvent} data as JSON. Strictly read-only,
 * {@code @SystemAdmin}-gated, paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/audit-events}.
 */
@Path("keyForgeNativeIIQ")
public class NativeAuditEventResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeAuditEventResource.class);
    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 200;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("audit-events")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuditEvents(@QueryParam("start") @DefaultValue("0") int start,
                                   @QueryParam("limit") @DefaultValue("200") int limit,
                                   @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "audit-events", start, limit, modifiedAfter);
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // Optional incremental bound (ISO-8601). These objects are append-only, so it bounds on "created".
        // Absent or unparseable -> null -> the full population (unchanged behaviour).
        java.util.Date createdAfter =
                com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST,
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "audit-events" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (createdAfter != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeAuditEventExtractionResult result =
                    new NativeAuditEventExtractionService().extract(context, safeStart, safeLimit, createdAfter);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=audit-events count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeAuditEventWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "audit-events", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "audit-events", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "audit-events", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "audit-events", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeAuditEventWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
