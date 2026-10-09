package com.keyforge.nativeiiq.task;

import com.keyforge.nativeiiq.model.NativeAccessHistoryExtractionResult;
import com.keyforge.nativeiiq.service.NativeAccessHistoryExtractionService;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;

/**
 * IIQ native runtime entry point for Access-History extraction. Deployed as a custom TaskDefinition
 * (executor = this class) inside IdentityIQ; IIQ supplies the live {@link SailPointContext}. READ-ONLY.
 *
 * <p>Extracts the three immutable Access-History object types via {@link NativeAccessHistoryExtractionService}
 * (which reads them through a context bound to {@code DatabaseInstance.ACCESS_HISTORY} — the objects are NOT
 * in the main IIQ datasource). This phase produces the rows in memory and reports per-type source vs
 * extracted counts on the TaskResult; the IIQ→our-DB transport is a later phase (do NOT assume the IIQ host
 * can reach our PostgreSQL).
 *
 * <p>Optional task arguments: {@code sourceSystem}, {@code start}, {@code limit} ({@code limit<=0} = all).
 * Extends {@code sailpoint.task.AbstractTaskExecutor}; if the target IIQ prefers a plugin base
 * ({@code BasePluginTaskExecutor}), swap the superclass — the execute/terminate contract is the same.
 */
public class NativeAccessHistoryExtractTask extends AbstractTaskExecutor {

    private volatile boolean terminated = false;

    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result,
                        Attributes<String, Object> args) throws Exception {
        String sourceSystem = arg(args, "sourceSystem");
        int start = argInt(args, "start", 0);
        int limit = argInt(args, "limit", 0); // 0 = all rows

        NativeAccessHistoryExtractionResult r =
                new NativeAccessHistoryExtractionService(sourceSystem).extract(context, start, limit);

        if (result != null) {
            result.setAttribute("extraction_run_id", r.getExtractionRunId());
            result.setAttribute("source_system", r.getSourceSystem());
            result.setAttribute("entitlement_captures_source_count",
                    Integer.valueOf(r.getEntitlementCaptureSourceCount()));
            result.setAttribute("entitlement_captures_extracted",
                    Integer.valueOf(r.getEntitlementCaptureCount()));
            result.setAttribute("identity_events_source_count",
                    Integer.valueOf(r.getIdentityEventSourceCount()));
            result.setAttribute("identity_events_extracted",
                    Integer.valueOf(r.getIdentityEventCount()));
            result.setAttribute("role_events_source_count",
                    Integer.valueOf(r.getRoleEventSourceCount()));
            result.setAttribute("role_events_extracted",
                    Integer.valueOf(r.getRoleEventCount()));
            result.setAttribute("certifications_source_count",
                    Integer.valueOf(r.getCertificationSourceCount()));
            result.setAttribute("certifications_extracted",
                    Integer.valueOf(r.getCertificationCount()));
            result.setAttribute("total_extracted", Integer.valueOf(r.getTotalCount()));
        }
    }

    @Override
    public boolean terminate() {
        this.terminated = true;
        return true;
    }

    private static String arg(Attributes<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int argInt(Attributes<String, Object> args, String key, int def) {
        String s = arg(args, key);
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
