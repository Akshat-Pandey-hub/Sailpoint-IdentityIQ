package com.keyforge.nativeiiq.resource;

import com.keyforge.nativeiiq.model.NativeTaskScheduleExtractionResult;
import com.keyforge.nativeiiq.service.NativeTaskScheduleExtractionService;
import com.keyforge.nativeiiq.wire.NativeTaskScheduleWire;

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
 * Plugin REST resource exporting native IdentityIQ {@code TaskSchedule} data (task cron schedules) as
 * JSON, read from the live object model via {@link SailPointContext}. Mirrors the validated native
 * resources: strictly read-only, {@code @SystemAdmin}-gated, paginated, fails safe against every
 * {@link Throwable}, and returns the envelope Map so the JAX-RS JSON provider serializes it once.
 *
 * <p>Full URL (from the IIQ base): {@code plugin/rest/keyForgeNativeIIQ/task-schedules?start=&limit=}.
 */
@Path("keyForgeNativeIIQ")
public class NativeTaskScheduleResource extends BasePluginResource {

    private static final Logger LOG = Logger.getLogger(NativeTaskScheduleResource.class.getName());

    static final int MAX_LIMIT = 1000;
    static final int DEFAULT_LIMIT = 100;

    @Override
    public String getPluginName() {
        return "KeyForgeNativeIIQ";
    }

    @GET
    @Path("task-schedules")
    @SystemAdmin
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTaskSchedules(@QueryParam("start") @DefaultValue("0") int start,
                                     @QueryParam("limit") @DefaultValue("100") int limit) {
        int safeStart = Math.max(0, start);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String stage = "start";
        try {
            LOG.fine("KeyForgeNativeIIQ task-schedules: entering (start=" + safeStart + ", limit=" + safeLimit + ")");

            stage = "getContext";
            SailPointContext context = getContext();

            stage = "extract";
            NativeTaskScheduleExtractionResult result =
                    new NativeTaskScheduleExtractionService().extract(context, safeStart, safeLimit);

            stage = "buildEnvelope";
            Map<String, Object> envelope = NativeTaskScheduleWire.envelope(result, safeStart, safeLimit);

            LOG.fine("KeyForgeNativeIIQ task-schedules: returning " + result.getCount() + " record(s)");
            return Response.ok(envelope).build();
        } catch (UnauthorizedAccessException e) {
            LOG.log(Level.WARNING, "KeyForgeNativeIIQ task-schedules: authorization denied at stage=" + stage, e);
            return error(Response.Status.FORBIDDEN, e);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "KeyForgeNativeIIQ task-schedules: failed at stage=" + stage, t);
            return error(Response.Status.INTERNAL_SERVER_ERROR, t);
        }
    }

    private static Response error(Response.Status status, Throwable t) {
        return Response.status(status)
                .entity(NativeTaskScheduleWire.errorEnvelope(status.getStatusCode(), t))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
