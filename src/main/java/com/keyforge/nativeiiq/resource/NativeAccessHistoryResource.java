package com.keyforge.nativeiiq.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.keyforge.nativeiiq.model.NativeAccessHistoryPage;
import com.keyforge.nativeiiq.model.NativeHistCertificationRow;
import com.keyforge.nativeiiq.model.NativeHistEntitlementCaptureRow;
import com.keyforge.nativeiiq.model.NativeHistIdentityEventRow;
import com.keyforge.nativeiiq.model.NativeHistRoleEventRow;
import com.keyforge.nativeiiq.service.NativeAccessHistoryExtractionService;
import com.keyforge.nativeiiq.wire.NativeAccessHistoryWire;

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
 * Plugin REST resource that exports native IdentityIQ <b>Access History</b> data as JSON, read from the live
 * object model. The source read is 100% native SailPoint Java API — the
 * {@link NativeAccessHistoryExtractionService} opens a {@code DatabaseInstance.ACCESS_HISTORY}
 * {@link SailPointContext} and runs the native extractors over {@code HistoricalEntitlementCapture},
 * {@code HistoricalIdentityEvent} and {@code HistoricalCertification}. This endpoint is ONLY the transport
 * that carries the already-extracted rows to the standalone loader; it is NOT the source model and uses NO
 * SCIM/REST objects to retrieve Access History.
 *
 * <p>URLs (from the IIQ base), one type per endpoint, paged {@code ?start=&limit=}:
 * <ul>
 *   <li>{@code plugin/rest/keyForgeNativeIIQ/access-history/entitlement-captures}</li>
 *   <li>{@code plugin/rest/keyForgeNativeIIQ/access-history/identity-events}</li>
 *   <li>{@code plugin/rest/keyForgeNativeIIQ/access-history/role-events}</li>
 *   <li>{@code plugin/rest/keyForgeNativeIIQ/access-history/certifications}</li>
 * </ul>
 *
 * <p><b>Strictly read-only</b> and {@code @SystemAdmin}-gated. Fails safe against every throwable (broader
 * than Exception, so a {@code NoClassDefFoundError} is contained as a JSON 500, never IIQ's exception.jsf).
 */
@Path("keyForgeNativeIIQ")
public class NativeAccessHistoryResource extends BasePluginResource {
    private static final Log LOG = LogFactory.getLog(NativeAccessHistoryResource.class);

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("access-history/entitlement-captures")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEntitlementCaptures(@QueryParam("start") @DefaultValue("0") int start,
                                           @QueryParam("limit") @DefaultValue("100") int limit,
                                           @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "access-history/entitlement-captures" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // Optional incremental bound (ISO-8601). These objects are append-only, so it bounds on "created".
        // Absent or unparseable -> null -> the full population (unchanged behaviour).
        java.util.Date createdAfter =
                com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return badRequest("HistoricalEntitlementCapture",
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "access-history/entitlement-captures" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (createdAfter != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeAccessHistoryPage<NativeHistEntitlementCaptureRow> page =
                    new NativeAccessHistoryExtractionService().extractEntitlementCaptures(context, safeStart, safeLimit, createdAfter);
            stage = "buildEnvelope";
            Map<String, Object> env = NativeAccessHistoryWire.entitlementCaptureEnvelope(page, safeStart, safeLimit);
            LOG.info("KeyForgeNativeIIQ result endpoint=access-history/entitlement-captures status=200 rows=" + page.getReturned() + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(env).build();
        } catch (UnauthorizedAccessException e) {
            return denied("HistoricalEntitlementCapture", stage, e);
        } catch (Throwable t) {
            return failed("HistoricalEntitlementCapture", stage, t);
        }
    }

    @GET
    @Path("access-history/identity-events")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentityEvents(@QueryParam("start") @DefaultValue("0") int start,
                                      @QueryParam("limit") @DefaultValue("100") int limit,
                                      @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "access-history/identity-events" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // Optional incremental bound (ISO-8601). These objects are append-only, so it bounds on "created".
        // Absent or unparseable -> null -> the full population (unchanged behaviour).
        java.util.Date createdAfter =
                com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return badRequest("HistoricalIdentityEvent",
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "access-history/identity-events" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (createdAfter != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeAccessHistoryPage<NativeHistIdentityEventRow> page =
                    new NativeAccessHistoryExtractionService().extractIdentityEvents(context, safeStart, safeLimit, createdAfter);
            stage = "buildEnvelope";
            Map<String, Object> env = NativeAccessHistoryWire.identityEventEnvelope(page, safeStart, safeLimit);
            LOG.info("KeyForgeNativeIIQ result endpoint=access-history/entitlement-captures status=200 rows=" + page.getReturned() + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            LOG.info("KeyForgeNativeIIQ result endpoint=access-history/identity-events status=200 rows=" + page.getReturned() + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(env).build();
        } catch (UnauthorizedAccessException e) {
            return denied("HistoricalIdentityEvent", stage, e);
        } catch (Throwable t) {
            return failed("HistoricalIdentityEvent", stage, t);
        }
    }

    @GET
    @Path("access-history/role-events")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoleEvents(@QueryParam("start") @DefaultValue("0") int start,
                                  @QueryParam("limit") @DefaultValue("100") int limit,
                                  @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "access-history/role-events" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // Optional incremental bound (ISO-8601). These objects are append-only, so it bounds on "created".
        // Absent or unparseable -> null -> the full population (unchanged behaviour).
        java.util.Date createdAfter =
                com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return badRequest("HistoricalRoleEvent",
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "access-history/role-events" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (createdAfter != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeAccessHistoryPage<NativeHistRoleEventRow> page =
                    new NativeAccessHistoryExtractionService().extractRoleEvents(context, safeStart, safeLimit, createdAfter);
            stage = "buildEnvelope";
            Map<String, Object> env = NativeAccessHistoryWire.roleEventEnvelope(page, safeStart, safeLimit);
            LOG.info("KeyForgeNativeIIQ result endpoint=access-history/role-events status=200 rows=" + page.getReturned() + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(env).build();
        } catch (UnauthorizedAccessException e) {
            return denied("HistoricalRoleEvent", stage, e);
        } catch (Throwable t) {
            return failed("HistoricalRoleEvent", stage, t);
        }
    }

    @GET
    @Path("access-history/certifications")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCertifications(@QueryParam("start") @DefaultValue("0") int start,
                                      @QueryParam("limit") @DefaultValue("100") int limit,
                                      @QueryParam("modifiedAfter") String modifiedAfter) {
        long requestStartedAt = System.currentTimeMillis();
        PluginLogLevel.apply(); LOG.info("KeyForgeNativeIIQ request endpoint=" + "access-history/certifications" + ", start=" + start + ", limit=" + limit + ", modifiedAfter=" + (modifiedAfter == null || modifiedAfter.trim().isEmpty() ? "<not set>" : LogText.safe(modifiedAfter)));
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // Optional incremental bound (ISO-8601). These objects are append-only, so it bounds on "created".
        // Absent or unparseable -> null -> the full population (unchanged behaviour).
        java.util.Date createdAfter =
                com.keyforge.nativeiiq.source.NativeIncrementalQuery.parseIsoToDate(modifiedAfter);
        if (com.keyforge.nativeiiq.source.NativeIncrementalQuery.isInvalid(modifiedAfter)) {
            return badRequest("HistoricalCertification",
                    com.keyforge.nativeiiq.source.NativeIncrementalQuery.invalidTimestamp());
        }
        String stage = "start";
        if (LOG.isDebugEnabled()) {
            LOG.debug("KeyForgeNativeIIQ query endpoint=" + "access-history/certifications" + ", firstRow=" + safeStart + ", resultLimit=" + safeLimit + ", incrementalFilter=" + (createdAfter != null));
        }
        try {
            stage = "getContext";
            SailPointContext context = getContext();
            stage = "extract";
            NativeAccessHistoryPage<NativeHistCertificationRow> page =
                    new NativeAccessHistoryExtractionService().extractCertifications(context, safeStart, safeLimit, createdAfter);
            stage = "buildEnvelope";
            Map<String, Object> env = NativeAccessHistoryWire.certificationEnvelope(page, safeStart, safeLimit);
            LOG.info("KeyForgeNativeIIQ result endpoint=access-history/certifications status=200 rows="
                    + page.getReturned() + ", elapsedMs=" + (System.currentTimeMillis() - requestStartedAt));
            return Response.ok(env).build();
        } catch (UnauthorizedAccessException e) {
            return denied("HistoricalCertification", stage, e);
        } catch (Throwable t) {
            return failed("HistoricalCertification", stage, t);
        }
    }

    private static Response denied(String entity, String stage, Throwable t) {
        LOG.warn("KeyForgeNativeIIQ access-history " + entity + ": denied at stage=" + stage, t);
        return Response.status(Response.Status.FORBIDDEN)
                .entity(NativeAccessHistoryWire.errorEnvelope(entity, Response.Status.FORBIDDEN.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON).build();
    }

    private static Response badRequest(String entity, Throwable t) {
        LOG.info("KeyForgeNativeIIQ request rejected endpoint=" + entity + " reason=" + LogText.safe(t == null ? null : t.getMessage()));
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(NativeAccessHistoryWire.errorEnvelope(entity, Response.Status.BAD_REQUEST.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON).build();
    }

    private static Response failed(String entity, String stage, Throwable t) {
        LOG.error("KeyForgeNativeIIQ access-history " + entity + ": failed at stage=" + stage, t);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(NativeAccessHistoryWire.errorEnvelope(
                        entity, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON).build();
    }
}
