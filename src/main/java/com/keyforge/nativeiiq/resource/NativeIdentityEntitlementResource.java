package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
    private static final Log LOG = LogFactory.getLog(NativeIdentityEntitlementResource.class);

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
                                            @QueryParam("limit") @DefaultValue("500") int limit,
                                            @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "identity-entitlements" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
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
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "identity-entitlements" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeIdentityEntitlementExtractionResult result =
                    new NativeIdentityEntitlementExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=identity-entitlements count=" + result.getSourceCount());
            }

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeIdentityEntitlementWire.envelope(result, safeStart, safeLimit);

            LOG.info("KeyForgeNativeIIQ identity-entitlements: returning " + result.getCount() + " record(s)" + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.warn("KeyForgeNativeIIQ identity-entitlements: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.error("KeyForgeNativeIIQ identity-entitlements: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            LOG.info("KeyForgeNativeIIQ request rejected endpoint=identityentitlement reason=" + LogText.safe(t == null ? null : t.getMessage()));
        }
        return Response.status(status)
                .entity(NativeIdentityEntitlementWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
