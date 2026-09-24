package com.keyforge.nativeiiq.resource;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plugin REST resource exporting native {@code SyslogEvent} data as JSON. Strictly read-only,
 * {@code @SystemAdmin}-gated, paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/syslog-events}.
 */
@Path("keyForgeNativeIIQ")
public class NativeSyslogEventResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeSyslogEventResource.class.getName());
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
                                    @QueryParam("limit") @DefaultValue("200") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeSyslogEventExtractionResult result =
                    new NativeSyslogEventExtractionService().extract(context, safeStart, safeLimit);
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeSyslogEventWire.envelope(result, safeStart, safeLimit);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ syslog-events: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ syslog-events: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeSyslogEventWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
