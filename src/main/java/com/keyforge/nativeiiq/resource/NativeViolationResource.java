package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeViolationExtractionResult;
import com.keyforge.nativeiiq.service.NativeViolationExtractionService;
import com.keyforge.nativeiiq.wire.NativeViolationWire;

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
 * Plugin REST resource exporting native {@code PolicyViolation} data as JSON. Strictly read-only,
 * {@code @SystemAdmin}-gated, paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/violations}.
 */
@Path("keyForgeNativeIIQ")
public class NativeViolationResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeViolationResource.class);
    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("violations")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getViolations(@QueryParam("start") @DefaultValue("0") int start,
                                  @QueryParam("limit") @DefaultValue("100") int limit,
                                  @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        LOG.info("KeyForgeNativeIIQ request endpoint=" + "violations" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
        java.util.Date bound = com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST, com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "violations" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (bound != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeViolationExtractionResult result =
                    new NativeViolationExtractionService().extract(context, safeStart, safeLimit, bound);
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeViolationWire.envelope(result, safeStart, safeLimit);
            LOG.info("KeyForgeNativeIIQ result endpoint=violations status=200 rows=" + result.getCount() + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.warn("KeyForgeNativeIIQ violations: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.error("KeyForgeNativeIIQ violations: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            LOG.info("KeyForgeNativeIIQ request rejected endpoint=violation reason=" + LogText.safe(t == null ? null : t.getMessage()));
        }
        return Response.status(status)
                .entity(NativeViolationWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
