package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativePolicyConstraintExtractionResult;
import com.keyforge.nativeiiq.service.NativePolicyConstraintExtractionService;
import com.keyforge.nativeiiq.wire.NativePolicyConstraintWire;
import com.keyforge.nativeiiq.source.NativeIncrementalQuery;

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
 * Plugin REST resource exporting native Policy constraint definitions as JSON, paging over the parent
 * Policy. Strictly read-only, {@code @SystemAdmin}-gated, fail-safe.
 * URL: {@code plugin/rest/keyForgeNativeIIQ/policy-constraints?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativePolicyConstraintResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativePolicyConstraintResource.class);
    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("policy-constraints")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPolicyConstraints(@QueryParam("start") @DefaultValue("0") int start,
                                         @QueryParam("limit") @DefaultValue("100") int limit,
                                         @QueryParam("modifiedAfter") String modifiedAfter,
                                         @QueryParam("includeCount") @DefaultValue("false") boolean includeCount) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "policy-constraints", start, limit, modifiedAfter);
        if (NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST, NativeIncrementalQuery.invalidTimestamp());
        }
        java.util.Date modifiedAfterDate = NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "policy-constraints" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativePolicyConstraintExtractionResult result =
                    new NativePolicyConstraintExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate, includeCount);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=policy-constraints count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativePolicyConstraintWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "policy-constraints", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "policy-constraints", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "policy-constraints", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "policy-constraints", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativePolicyConstraintWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
