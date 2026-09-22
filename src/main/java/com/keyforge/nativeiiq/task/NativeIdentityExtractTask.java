package com.keyforge.nativeiiq.task;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeExtractionResult;
import com.keyforge.nativeiiq.source.NativeIdentityExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;

/**
 * IIQ native runtime entry point for Stage-1 Identity extraction. Deployed as a custom TaskDefinition
 * (executor = this class) inside IdentityIQ; IIQ supplies the live {@link SailPointContext}. READ-ONLY.
 *
 * <p>Optional task arguments: {@code sourceSystem, batchSize, identityLimit, outputMode} (else env / -D
 * defaults). This phase produces the {@link NativeExtractionResult} in memory and reports counts on the
 * TaskResult; the IIQ→our-DB transport is a later phase (do NOT assume the IIQ host can reach our
 * PostgreSQL).
 *
 * <p>Extends {@code sailpoint.task.AbstractTaskExecutor} (implements {@code sailpoint.object.TaskExecutor}).
 * If the target IIQ version prefers a plugin base ({@code BasePluginTaskExecutor}), swap the superclass —
 * the {@code execute}/{@code terminate} contract is the same. Confirmed at compile time against the jar.
 */
public class NativeIdentityExtractTask extends AbstractTaskExecutor {

    private volatile boolean terminated = false;

    @Override
    public void execute(SailPointContext context, TaskSchedule schedule, TaskResult result,
                        Attributes<String, Object> args) throws Exception {
        NativeExtractionConfig cfg = NativeExtractionConfig.of(
                arg(args, "sourceSystem"),
                argInt(args, "batchSize"),
                argInt(args, "identityLimit"),
                arg(args, "outputMode"),
                null);

        NativeExtractionResult r = new NativeIdentityExtractor(context, cfg).extract();

        if (result != null) {
            result.setAttribute("extraction_run_id", r.getExtractionRunId());
            result.setAttribute("source_system", r.getSourceSystem());
            result.setAttribute("entity", r.getEntityType());
            result.setAttribute("identities_extracted", Integer.valueOf(r.getCount()));
            result.setAttribute("output_mode", cfg.getOutputMode());
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

    private static Integer argInt(Attributes<String, Object> args, String key) {
        String s = arg(args, key);
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
