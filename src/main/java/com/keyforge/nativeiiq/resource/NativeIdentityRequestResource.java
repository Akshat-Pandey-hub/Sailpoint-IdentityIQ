package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeIdentityRequestExtractionResult;
import com.keyforge.nativeiiq.service.NativeIdentityRequestExtractionService;
import com.keyforge.nativeiiq.wire.NativeIdentityRequestWire;

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
 * Plugin REST resource exporting native {@code IdentityRequest} data (each with its derived items and
 * request-time approval summaries) as JSON. Read-only, {@code @SystemAdmin}, paginated, fail-safe.
 * URL: {@code plugin/rest/keyForgeNativeIIQ/identity-requests?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeIdentityRequestResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeIdentityRequestResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("identity-requests")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentityRequests(@QueryParam("start") @DefaultValue("0") int start,
                                        @QueryParam("limit") @DefaultValue("100") int limit,
                                        @QueryParam("modifiedAfter") String modifiedAfter,
                                        @QueryParam("includeCount") @DefaultValue("false") boolean includeCount) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "identity-requests", start, limit, modifiedAfter);
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
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "identity-requests" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeIdentityRequestExtractionResult result =
                    new NativeIdentityRequestExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate, includeCount);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=identity-requests count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeIdentityRequestWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "identity-requests", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "identity-requests", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "identity-requests", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "identity-requests", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeIdentityRequestWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
