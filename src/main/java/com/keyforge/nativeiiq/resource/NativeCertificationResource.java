package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeCertificationExtractionResult;
import com.keyforge.nativeiiq.service.NativeCertificationExtractionService;
import com.keyforge.nativeiiq.wire.NativeCertificationWire;

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
 * Plugin REST resource exporting native {@code Certification} data as JSON. Read-only, {@code @SystemAdmin},
 * paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/certifications?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeCertificationResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeCertificationResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("certifications")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCertifications(@QueryParam("start") @DefaultValue("0") int start,
                                      @QueryParam("limit") @DefaultValue("100") int limit,
                                      @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "certifications", start, limit, modifiedAfter);
        java.util.Date bound = com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST, com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "certifications" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (bound != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeCertificationExtractionResult result =
                    new NativeCertificationExtractionService().extract(context, safeStart, safeLimit, bound);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=certifications count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeCertificationWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "certifications", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "certifications", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "certifications", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "certifications", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeCertificationWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
