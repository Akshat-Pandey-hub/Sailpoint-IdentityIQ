package com.keyforge.nativeiiq.resource;

import com.keyforge.nativeiiq.model.NativeIdentityRoleExtractionResult;
import com.keyforge.nativeiiq.service.NativeIdentityRoleExtractionService;
import com.keyforge.nativeiiq.wire.NativeIdentityRoleWire;

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
 * Plugin REST resource exporting native identity&nbsp;&harr;&nbsp;role edges (from Identity role
 * assignments/detections). The {@code start}/{@code limit} window pages over {@code Identity}, not edges.
 * Read-only, {@code @SystemAdmin}, fail-safe. URL: {@code plugin/rest/keyForgeNativeIIQ/identity-roles}.
 */
@Path("keyForgeNativeIIQ")
public class NativeIdentityRoleResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeIdentityRoleResource.class.getName());

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 200;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("identity-roles")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentityRoles(@QueryParam("start") @DefaultValue("0") int start,
                                     @QueryParam("limit") @DefaultValue("200") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeIdentityRoleExtractionResult result =
                    new NativeIdentityRoleExtractionService().extract(context, safeStart, safeLimit);
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeIdentityRoleWire.envelope(result, safeStart, safeLimit);
            LOG.fine("KeyForgeNativeIIQ identity-roles: returning " + result.getCount() + " edge(s)");
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ identity-roles: denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ identity-roles: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeIdentityRoleWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
