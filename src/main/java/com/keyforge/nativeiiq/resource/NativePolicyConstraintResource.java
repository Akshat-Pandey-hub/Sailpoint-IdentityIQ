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
                                         @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "policy-constraints" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
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
                    new NativePolicyConstraintExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=policy-constraints count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativePolicyConstraintWire.envelope(result, safeStart, safeLimit);
            LOG.info("KeyForgeNativeIIQ result endpoint=policy-constraints status=200 rows=" + result.getCount() + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.warn("KeyForgeNativeIIQ policy-constraints: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.error("KeyForgeNativeIIQ policy-constraints: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            LOG.info("KeyForgeNativeIIQ request rejected endpoint=policyconstraint reason=" + LogText.safe(t == null ? null : t.getMessage()));
        }
        return Response.status(status)
                .entity(NativePolicyConstraintWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
