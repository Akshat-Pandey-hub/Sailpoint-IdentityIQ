package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeWorkgroupExtractionResult;
import com.keyforge.nativeiiq.service.NativeWorkgroupExtractionService;
import com.keyforge.nativeiiq.wire.NativeWorkgroupWire;

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
 * Plugin REST resource exporting native IdentityIQ Workgroup data (workgroup {@code Identity} objects)
 * as JSON, read from the live object model via {@link SailPointContext}. Mirrors the validated native
 * resources: strictly read-only, {@code @SystemAdmin}-gated, paginated, fails safe against every
 * {@link Throwable}, and returns the envelope Map so the JAX-RS JSON provider serializes it to a JSON
 * object exactly once.
 *
 * <p>Full URL (from the IIQ base): {@code plugin/rest/keyForgeNativeIIQ/workgroups?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeWorkgroupResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeWorkgroupResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("workgroups")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkgroups(@QueryParam("start") @DefaultValue("0") int start,
                                  @QueryParam("limit") @DefaultValue("100") int limit,
                                  @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        LOG.info("KeyForgeNativeIIQ request endpoint=" + "workgroups" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
        java.util.Date bound = com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST, com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "workgroups" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (bound != null));
        }
        try {
            LOG.debug("KeyForgeNativeIIQ workgroups: entering (start=" + safeStart + ", limit=" + safeLimit + ")");

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeWorkgroupExtractionResult result =
                    new NativeWorkgroupExtractionService().extract(context, safeStart, safeLimit, bound);

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeWorkgroupWire.envelope(result, safeStart, safeLimit);

            LOG.info("KeyForgeNativeIIQ workgroups: returning " + result.getCount() + " record(s)" + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            // Return the Map so the provider serializes once to a JSON object (never a double-encoded String).
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.warn("KeyForgeNativeIIQ workgroups: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.error("KeyForgeNativeIIQ workgroups: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    /** Returns the error map as the entity; the JSON provider serializes it once (a JSON object). */
    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            LOG.info("KeyForgeNativeIIQ request rejected endpoint=workgroup reason=" + LogText.safe(t == null ? null : t.getMessage()));
        }
        return Response.status(status)
                .entity(NativeWorkgroupWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
