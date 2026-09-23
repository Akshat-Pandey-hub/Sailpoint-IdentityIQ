package com.keyforge.nativeiiq.resource;

import com.keyforge.nativeiiq.model.NativeProvisioningTxnExtractionResult;
import com.keyforge.nativeiiq.service.NativeProvisioningTxnExtractionService;
import com.keyforge.nativeiiq.wire.NativeProvisioningTxnWire;

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
 * Plugin REST resource exporting native {@code ProvisioningTransaction} data (each with its derived
 * provisioning items) as JSON. Read-only, {@code @SystemAdmin}, paginated, fail-safe.
 * URL: {@code plugin/rest/keyForgeNativeIIQ/provisioning-transactions?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeProvisioningTxnResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeProvisioningTxnResource.class.getName());

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("provisioning-transactions")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProvisioningTransactions(@QueryParam("start") @DefaultValue("0") int start,
                                                @QueryParam("limit") @DefaultValue("100") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeProvisioningTxnExtractionResult result =
                    new NativeProvisioningTxnExtractionService().extract(context, safeStart, safeLimit);
            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeProvisioningTxnWire.envelope(result, safeStart, safeLimit);
            LOG.fine("KeyForgeNativeIIQ provisioning-transactions: returning " + result.getCount() + " record(s)");
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ provisioning-transactions: denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ provisioning-transactions: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeProvisioningTxnWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
