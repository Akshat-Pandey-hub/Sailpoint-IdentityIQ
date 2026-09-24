package com.keyforge.nativeiiq.resource;

import com.keyforge.nativeiiq.model.NativeWorkItemExtractionResult;
import com.keyforge.nativeiiq.service.NativeWorkItemExtractionService;
import com.keyforge.nativeiiq.wire.NativeWorkItemWire;

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
 * Plugin REST resource exporting native live {@code WorkItem} data as JSON. Read-only, {@code @SystemAdmin},
 * paginated, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/workitems?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeWorkItemResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeWorkItemResource.class.getName());

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("workitems")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkItems(@QueryParam("start") @DefaultValue("0") int start,
                                 @QueryParam("limit") @DefaultValue("100") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeWorkItemExtractionResult result =
                    new NativeWorkItemExtractionService().extract(context, safeStart, safeLimit);
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeWorkItemWire.envelope(result, safeStart, safeLimit);
            LOG.fine("KeyForgeNativeIIQ workitems: returning " + result.getCount() + " record(s)");
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ workitems: denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ workitems: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeWorkItemWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
