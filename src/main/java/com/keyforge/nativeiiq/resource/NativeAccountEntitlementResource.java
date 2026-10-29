package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
    private static final Log LOG = LogFactory.getLog(NativeAccountEntitlementResource.class);

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
                                           @QueryParam("limit") @DefaultValue("200") int limit,
                                           @QueryParam("modifiedAfter") String modifiedAfter,
                                           @QueryParam("includeCount") @DefaultValue("false") boolean includeCount) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLog.request(LOG, "account-entitlements", start, limit, modifiedAfter);
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
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "account-entitlements" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeAccountEntitlementExtractionResult result =
                    new NativeAccountEntitlementExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate, includeCount);
            if (LOG.isDebugEnabled()) {
                LOG.debug("KeyForgeNativeIIQ source endpoint=account-entitlements count=" + result.getSourceCount());
            }

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeAccountEntitlementWire.envelope(result, safeStart, safeLimit);

            PluginLog.result(LOG, "account-entitlements", result.getCount(), requestStartedAt);
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            PluginLog.denied(LOG, "account-entitlements", stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            PluginLog.failed(LOG, "account-entitlements", stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            PluginLog.rejected(LOG, "account-entitlements", t == null ? null : t.getMessage());
        }
        return Response.status(status)
                .entity(NativeAccountEntitlementWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
