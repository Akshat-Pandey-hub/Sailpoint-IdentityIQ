package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeIdentityRoleExtractionResult;
import com.keyforge.nativeiiq.service.NativeIdentityRoleExtractionService;
import com.keyforge.nativeiiq.source.NativeIncrementalQuery;
import com.keyforge.nativeiiq.wire.NativeIdentityRoleWire;

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
 * Plugin REST resource exporting native identity&nbsp;&harr;&nbsp;role edges (from Identity role
 * assignments/detections). The {@code start}/{@code limit} window pages over {@code Identity}, not edges.
 * Read-only, {@code @SystemAdmin}, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/identity-roles}.
 */
@Path("keyForgeNativeIIQ")
public class NativeIdentityRoleResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeIdentityRoleResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 200;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("identity-roles")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentityRoles(@QueryParam("start") @DefaultValue("0") int start,
                                     @QueryParam("limit") @DefaultValue("200") int limit,
                                     @QueryParam("modifiedAfter") String modifiedAfter,
                                     @QueryParam("includeCount") @DefaultValue("false") boolean includeCount) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "identity-roles", start, limit, modifiedAfter);
        if (NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST, NativeIncrementalQuery.invalidTimestamp());
        }
        java.util.Date modifiedAfterDate = NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "identity-roles" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeIdentityRoleExtractionResult result =
                    new NativeIdentityRoleExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate, includeCount);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=identity-roles count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeIdentityRoleWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "identity-roles", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "identity-roles", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "identity-roles", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "identity-roles", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeIdentityRoleWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
