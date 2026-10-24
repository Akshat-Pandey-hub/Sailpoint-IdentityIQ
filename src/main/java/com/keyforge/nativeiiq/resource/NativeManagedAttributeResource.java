package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeManagedAttributeExtractionResult;
import com.keyforge.nativeiiq.service.NativeManagedAttributeExtractionService;
import com.keyforge.nativeiiq.source.NativeManagedAttributeInspector;
import com.keyforge.nativeiiq.wire.NativeManagedAttributeWire;

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
 * Plugin REST resource exporting native IdentityIQ ManagedAttribute (entitlement) data as JSON, read
 * from the live object model via {@link SailPointContext}. Mirrors {@code NativeIdentityResource}:
 * strictly read-only, {@code SystemAdministrator}-authorized, paginated, and fails safe against every
 * {@link Throwable} (returns a JSON 403/500 rather than redirecting to IIQ's {@code exception.jsf}).
 *
 * <p>Full URL (from the IIQ base): {@code plugin/rest/keyForgeNativeIIQ/entitlements?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeManagedAttributeResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeManagedAttributeResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("entitlements")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEntitlements(@QueryParam("start") @DefaultValue("0") int start,
                                    @QueryParam("limit") @DefaultValue("100") int limit,
                                    @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "entitlements" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        java.util.Date modifiedAfterDate =
                com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return error(Response.Status.BAD_REQUEST,
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        // Authorization enforced by IIQ's PluginAuthorizationFilter via @SystemAdmin (required 8.4 gate).
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "entitlements" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (modifiedAfterDate != null));
        }
        try {
            LOG.debug("KeyForgeNativeIIQ entitlements: entering (start=" + safeStart + ", limit=" + safeLimit + ")");

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeManagedAttributeExtractionResult result =
                    new NativeManagedAttributeExtractionService().extract(context, safeStart, safeLimit, modifiedAfterDate);

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeManagedAttributeWire.envelope(result, safeStart, safeLimit);

            LOG.info("KeyForgeNativeIIQ entitlements: returning " + result.getCount() + " record(s)" + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            // Hand the JAX-RS JSON provider the Map so it is serialized to a JSON OBJECT exactly once
            // (passing a JsonHelper.toJson(...) String double-encodes it into a quoted JSON string).
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.warn("KeyForgeNativeIIQ entitlements: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.error("KeyForgeNativeIIQ entitlements: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    /**
     * SOURCE-TRUTH inspection endpoint: returns the ACTUAL native ManagedAttribute object model — every
     * native getter value, the raw {@code getAttributes()} map (original keys), and IIQ's own
     * {@code XMLObjectFactory} serialization — with NO renaming into KeyForge terms and NO kf_entitlement
     * mapping. Read-only. {@code limit<=0} (default) returns the full population.
     *
     * <p>Full URL: {@code plugin/rest/keyForgeNativeIIQ/entitlements-source?start=&limit=&includeXml=}.
     */
    @GET
    @Path("entitlements-source")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEntitlementsSource(@QueryParam("start") @DefaultValue("0") int start,
                                          @QueryParam("limit") @DefaultValue("0") int limit,
                                          @QueryParam("includeXml") @DefaultValue("true") boolean includeXml) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "entitlements-source" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + "<not set>");
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "entitlements-source" + ", firstRow=" + start + ", resultLimit=" + limit + ", incrementalFilter=" + false);
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "inspect";
            Map<String, Object> envelope =
                    new NativeManagedAttributeInspector(includeXml).inspect(context, Math.max(0, start), limit);
            LOG.info("KeyForgeNativeIIQ result endpoint=entitlements-source status=200 rows="
                    + envelope.get("count") + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.warn("KeyForgeNativeIIQ entitlements-source: denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.error("KeyForgeNativeIIQ entitlements-source: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    /** Returns the error map as the entity; the JSON provider serializes it once (a JSON object). */
    private static Response error(Response.Status status, Throwable t) {
        if (status == Response.Status.BAD_REQUEST) {
            LOG.info("KeyForgeNativeIIQ request rejected endpoint=managedattribute reason=" + LogText.safe(t == null ? null : t.getMessage()));
        }
        return Response.status(status)
                .entity(NativeManagedAttributeWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
