package com.keyforge.nativeiiq.resource;

import com.keyforge.nativeiiq.model.NativeAccountEntitlementExtractionResult;
import com.keyforge.nativeiiq.service.NativeAccountEntitlementExtractionService;
import com.keyforge.nativeiiq.wire.NativeAccountEntitlementWire;

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
 * Plugin REST resource exporting native account&nbsp;&harr;&nbsp;entitlement edges (from {@code Link}
 * entitlement attributes) as JSON. The {@code start}/{@code limit} window pages over {@code Link}s (the
 * source unit), not over edges. Strictly read-only, {@code @SystemAdmin}-gated, fails safe against every
 * {@link Throwable}, and returns the envelope Map so the JSON provider serializes it once.
 *
 * <p>Full URL: {@code plugin/rest/keyForgeNativeIIQ/account-entitlements?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeAccountEntitlementResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeAccountEntitlementResource.class.getName());

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 200;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("account-entitlements")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAccountEntitlements(@QueryParam("start") @DefaultValue("0") int start,
                                           @QueryParam("limit") @DefaultValue("200") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        try {
            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeAccountEntitlementExtractionResult result =
                    new NativeAccountEntitlementExtractionService().extract(context, safeStart, safeLimit);

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeAccountEntitlementWire.envelope(result, safeStart, safeLimit);

            LOG.fine("KeyForgeNativeIIQ account-entitlements: returning " + result.getCount() + " edge(s)");
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ account-entitlements: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ account-entitlements: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeAccountEntitlementWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
