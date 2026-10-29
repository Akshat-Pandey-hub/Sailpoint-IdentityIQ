package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeCertificationEntityExtractionResult;
import com.keyforge.nativeiiq.service.NativeCertificationEntityExtractionService;
import com.keyforge.nativeiiq.wire.NativeCertificationEntityWire;

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
 * Plugin REST resource exporting native {@code CertificationEntity} data as JSON. Read-only, {@code @SystemAdmin},
 * paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/certification-entities?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeCertificationEntityResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeCertificationEntityResource.class);

    static final int MAX_LIMIT = 2000;
    static final int DEFAULT_LIMIT = 500;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("certification-entities")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCertificationEntities(@QueryParam("start") @DefaultValue("0") int start,
                                             @QueryParam("limit") @DefaultValue("500") int limit,
                                             @QueryParam("modifiedAfter") String modifiedAfter,
                                             @QueryParam("includeCount") @DefaultValue("false") boolean includeCount) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "certification-entities", start, limit, modifiedAfter);
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
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "certification-entities" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeCertificationEntityExtractionResult result =
                    new NativeCertificationEntityExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate, includeCount);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=certification-entities count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeCertificationEntityWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "certification-entities", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "certification-entities", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "certification-entities", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "certification-entities", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeCertificationEntityWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
