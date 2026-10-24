package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativePolicyExtractionResult;
import com.keyforge.nativeiiq.service.NativePolicyExtractionService;
import com.keyforge.nativeiiq.wire.NativePolicyWire;

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
 * Plugin REST resource exporting native {@code Policy} data as JSON. Strictly read-only,
 * {@code @SystemAdmin}-gated, paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/policies}.
 */
@Path("keyForgeNativeIIQ")
public class NativePolicyResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativePolicyResource.class);
    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("policies")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPolicies(@QueryParam("start") @DefaultValue("0") int start,
                                @QueryParam("limit") @DefaultValue("100") int limit,
                                @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "policies", start, limit, modifiedAfter);
        java.util.Date bound = com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST, com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "policies" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (bound != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativePolicyExtractionResult result =
                    new NativePolicyExtractionService().extract(context, safeStart, safeLimit, bound);
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativePolicyWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "policies", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "policies", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "policies", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "policies", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativePolicyWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
