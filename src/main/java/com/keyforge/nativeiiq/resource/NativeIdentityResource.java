package com.keyforge.nativeiiq.resource;

import com.keyforge.nativeiiq.model.NativeExtractionResult;
import com.keyforge.nativeiiq.service.NativeIdentityExtractionService;
import com.keyforge.nativeiiq.wire.NativeIdentityWire;

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
 * Plugin REST resource that exports native IdentityIQ Identity data as JSON, read from the live
 * object model via the plugin runtime's {@link SailPointContext}. This is the "get the data out of
 * IIQ" bridge for the KeyForge native workstream: our standalone application pulls this endpoint over
 * the same authenticated HTTPS channel it already uses for IIQ REST/SCIM, then loads the payload into
 * our PostgreSQL {@code iiq_native} schema.
 *
 * <p>Full URL (from the IIQ base): {@code plugin/rest/keyForgeNativeIIQ/identities?start=&limit=}.
 *
 * <p><b>Strictly read-only.</b> It only runs {@link NativeIdentityExtractor} (search / getObjectById /
 * decache) and serializes the result; it never mutates IdentityIQ. Access is restricted to callers
 * with the {@code SystemAdministrator} capability.
 *
 * <p><b>Fails safe against every throwable.</b> The whole body is wrapped in a {@code catch (Throwable)}
 * — deliberately broader than {@code Exception} — because IIQ's plugin REST framework only maps
 * exceptions (every registered mapper extends {@code AbstractExceptionMapper<T extends Exception>}), so
 * a {@link java.lang.Error} (e.g. {@code NoClassDefFoundError}/{@code NoSuchMethodError}/
 * {@code LinkageError}) would otherwise escape to IIQ's HTML {@code exception.jsf} incident page. Here
 * it is contained, logged server-side with its full stack, and returned as a clean 500 JSON body that
 * names the exact throwable type + message. A {@link UnauthorizedAccessException} maps to 403.
 */
@Path("keyForgeNativeIIQ")
public class NativeIdentityResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeIdentityResource.class.getName());

    /** Hard cap so a single request can never ask for an unbounded page. */
    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    /**
     * Returns one stable-ordered page of native Identity rows. Never throws to the container: on any
     * failure (including {@link java.lang.Error}) it returns a JSON error body instead of redirecting
     * to {@code exception.jsf}.
     *
     * @param start zero-based offset of the first row (default 0)
     * @param limit maximum rows to return (default {@value #DEFAULT_LIMIT}, capped at {@value #MAX_LIMIT})
     */
    @GET
    @Path("identities")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentities(@QueryParam("start") @DefaultValue("0") int start,
                                  @QueryParam("limit") @DefaultValue("100") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        // Boundary logging: records the last successful stage; no credentials or Identity data.
        // Authorization is enforced by IIQ's PluginAuthorizationFilter via the @SystemAdmin annotation
        // above (the caller must be a System Administrator) — the required IIQ 8.4 plugin-REST gate.
        String stage = "start";
        try {
            LOG.fine("KeyForgeNativeIIQ identities: entering (start=" + safeStart + ", limit=" + safeLimit + ")");

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeExtractionResult result =
                    new NativeIdentityExtractionService().extract(context, safeStart, safeLimit);

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeIdentityWire.envelope(result, safeStart, safeLimit);

            LOG.fine("KeyForgeNativeIIQ identities: returning " + result.getCount() + " record(s)");
            // Hand the JAX-RS JSON provider the Map so it is serialized to a JSON OBJECT exactly once.
            // (Passing a JsonHelper.toJson(...) String here made the provider serialize it a SECOND
            //  time, producing a double-encoded quoted JSON string.)
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ identities: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            // Broader than Exception on purpose (see class javadoc): contain Errors too so the caller
            // gets a diagnosable JSON 500 and the server log keeps the full stack — never exception.jsf.
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ identities: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    /**
     * Clean JSON error body (never contains Identity data). Returns the error map as the entity so the
     * JAX-RS JSON provider serializes it once to a JSON object — same single-encoding contract as the
     * success path (the map is tiny Strings/ints, so provider serialization cannot fail on it).
     */
    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeIdentityWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
