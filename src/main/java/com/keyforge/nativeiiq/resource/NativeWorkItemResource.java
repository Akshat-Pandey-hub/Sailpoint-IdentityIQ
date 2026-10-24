package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeWorkItemExtractionResult;
import com.keyforge.nativeiiq.service.NativeWorkItemExtractionService;
import com.keyforge.nativeiiq.wire.NativeWorkItemWire;

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
 * Plugin REST resource exporting native live {@code WorkItem} data as JSON. Read-only, {@code @SystemAdmin},
 * paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/workitems?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeWorkItemResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeWorkItemResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("workitems")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkItems(@QueryParam("start") @DefaultValue("0") int start,
                                 @QueryParam("limit") @DefaultValue("100") int limit,
                                 @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "workitems", start, limit, modifiedAfter);
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // Optional incremental bound (ISO-8601): objects changed after it (modified after it, or never modified and
        // created after it). Absent or unparseable -> null -> the full population (unchanged behaviour).
        java.util.Date modifiedAfterDate =
                com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST,
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "workitems" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeWorkItemExtractionResult result =
                    new NativeWorkItemExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=workitems count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeWorkItemWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "workitems", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "workitems", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "workitems", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "workitems", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeWorkItemWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
