package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeCertificationArchiveExtractionResult;
import com.keyforge.nativeiiq.service.NativeCertificationArchiveExtractionService;
import com.keyforge.nativeiiq.wire.NativeCertificationArchiveWire;

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
 * Plugin REST resource exporting native IdentityIQ {@code CertificationArchive} data (the immutable
 * CEC/history record of completed certifications) as JSON, read from the live object model via
 * {@link SailPointContext}. Mirrors the validated native resources: strictly read-only,
 * {@code @SystemAdmin}-gated, paginated, fails safe against every {@link Throwable}, and returns the envelope
 * Map so the JAX-RS JSON provider serializes it to a JSON object exactly once.
 *
 * <p>Full URL (from the IIQ base): {@code plugin/rest/keyForgeNativeIIQ/certification-archives?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeCertificationArchiveResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeCertificationArchiveResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("certification-archives")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCertificationArchives(@QueryParam("start") @DefaultValue("0") int start,
                                             @QueryParam("limit") @DefaultValue("100") int limit,
                                             @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "certification-archives", start, limit, modifiedAfter);
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
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "certification-archives" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (createdAfter != null));
        }
        try {
            LOG.debug("KeyForgeNativeIIQ certification-archives: entering (start=" + safeStart + ", limit=" + safeLimit + ")");

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeCertificationArchiveExtractionResult result =
                    new NativeCertificationArchiveExtractionService().extract(context, safeStart, safeLimit, createdAfter);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=certification-archives count=" + result.getSourceCount());
            }

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeCertificationArchiveWire.envelope(result, safeStart, safeLimit);

            PluginLog.result(LOG, "certification-archives", result.getCount(), requestStartedAt);
            // Return the Map so the provider serializes once to a JSON object (never a double-encoded String).
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "certification-archives", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "certification-archives", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    /** Returns the error map as the entity; the JSON provider serializes it once (a JSON object). */
    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "certification-archives", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeCertificationArchiveWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
