package com.keyforge.nativeiiq.resource;

import com.keyforge.nativeiiq.model.NativeIdentityEntitlementExtractionResult;
import com.keyforge.nativeiiq.service.NativeIdentityEntitlementExtractionService;
import com.keyforge.nativeiiq.wire.NativeIdentityEntitlementWire;

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
 * Plugin REST resource exporting native IdentityIQ {@code IdentityEntitlement} data (identity&nbsp;&harr;&nbsp;
 * entitlement with provenance) as JSON, read from the live object model via {@link SailPointContext}.
 * Strictly read-only, {@code @SystemAdmin}-gated, paginated, fails safe against every {@link Throwable},
 * and returns the envelope Map so the JAX-RS JSON provider serializes it to a JSON object exactly once.
 *
 * <p>Full URL (from the IIQ base): {@code plugin/rest/keyForgeNativeIIQ/identity-entitlements?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeIdentityEntitlementResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeIdentityEntitlementResource.class.getName());

    static final int MAX_LIMIT = 2000;
    static final int DEFAULT_LIMIT = 500;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("identity-entitlements")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentityEntitlements(@QueryParam("start") @DefaultValue("0") int start,
                                            @QueryParam("limit") @DefaultValue("500") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        try {
            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeIdentityEntitlementExtractionResult result =
                    new NativeIdentityEntitlementExtractionService().extract(context, safeStart, safeLimit);

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeIdentityEntitlementWire.envelope(result, safeStart, safeLimit);

            LOG.fine("KeyForgeNativeIIQ identity-entitlements: returning " + result.getCount() + " record(s)");
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ identity-entitlements: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ identity-entitlements: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeIdentityEntitlementWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
