package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeGroupDefinitionExtractionResult;
import com.keyforge.nativeiiq.service.NativeGroupDefinitionExtractionService;
import com.keyforge.nativeiiq.wire.NativeGroupDefinitionWire;

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
 * Plugin REST resource exporting native IdentityIQ {@code GroupDefinition} data (Populations and
 * Groups) as JSON, read from the live object model via {@link SailPointContext}. Mirrors the validated
 * native resources: strictly read-only, {@code @SystemAdmin}-gated, paginated, fails safe against every
 * {@link Throwable}, and returns the envelope Map so the JAX-RS JSON provider serializes it to a JSON
 * object exactly once.
 *
 * <p>Full URL (from the IIQ base): {@code plugin/rest/keyForgeNativeIIQ/group-definitions?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeGroupDefinitionResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeGroupDefinitionResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("group-definitions")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGroupDefinitions(@QueryParam("start") @DefaultValue("0") int start,
                                        @QueryParam("limit") @DefaultValue("100") int limit,
                                        @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "group-definitions", start, limit, modifiedAfter);
        java.util.Date bound = com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST, com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "group-definitions" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (bound != null));
        }
        try {
            LOG.debug("KeyForgeNativeIIQ group-definitions: entering (start=" + safeStart + ", limit=" + safeLimit + ")");

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeGroupDefinitionExtractionResult result =
                    new NativeGroupDefinitionExtractionService().extract(context, safeStart, safeLimit, bound);

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeGroupDefinitionWire.envelope(result, safeStart, safeLimit);

            PluginLog.result(LOG, "group-definitions", result.getCount(), requestStartedAt);
            // Return the Map so the provider serializes once to a JSON object (never a double-encoded String).
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "group-definitions", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "group-definitions", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    /** Returns the error map as the entity; the JSON provider serializes it once (a JSON object). */
    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "group-definitions", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeGroupDefinitionWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
