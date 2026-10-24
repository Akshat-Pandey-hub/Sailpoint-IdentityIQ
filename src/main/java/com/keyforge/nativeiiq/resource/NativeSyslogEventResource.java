package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeSyslogEventExtractionResult;
import com.keyforge.nativeiiq.service.NativeSyslogEventExtractionService;
import com.keyforge.nativeiiq.wire.NativeSyslogEventWire;

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
 * Plugin REST resource exporting native {@code SyslogEvent} data as JSON. Strictly read-only,
 * {@code @SystemAdmin}-gated, paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/syslog-events}.
 */
@Path("keyForgeNativeIIQ")
public class NativeSyslogEventResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeSyslogEventResource.class);
    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 200;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("syslog-events")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSyslogEvents(@QueryParam("start") @DefaultValue("0") int start,
                                    @QueryParam("limit") @DefaultValue("200") int limit,
                                    @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "syslog-events", start, limit, modifiedAfter);
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
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "syslog-events" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (createdAfter != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeSyslogEventExtractionResult result =
                    new NativeSyslogEventExtractionService().extract(context, safeStart, safeLimit, createdAfter);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=syslog-events count=" + result.getSourceCount());
            }
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeSyslogEventWire.envelope(result, safeStart, safeLimit);
            PluginLog.result(LOG, "syslog-events", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "syslog-events", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "syslog-events", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "syslog-events", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeSyslogEventWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
