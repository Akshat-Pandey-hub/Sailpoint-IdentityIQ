package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeTaskScheduleExtractionResult;
import com.keyforge.nativeiiq.model.NativeTaskScheduleRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeTaskScheduleExtractionResult} into a plain JSON-friendly structure (only Maps,
 * Lists, Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep)
 * but bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeTaskScheduleFields}.
 */
public final class NativeTaskScheduleWire {

    private NativeTaskScheduleWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "TaskSchedule");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeTaskScheduleExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeTaskScheduleRow r : result.getTaskSchedules()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "TaskSchedule");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeTaskScheduleRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("definitionName", r.getDefinitionName());
        m.put("state", r.getState());
        m.put("newState", r.getNewState());
        m.put("launcher", r.getLauncher());
        m.put("host", r.getHost());
        m.put("lastLaunchError", r.getLastLaunchError());
        m.put("deleteOnFinish", r.getDeleteOnFinish());
        m.put("lastExecution", iso(r.getLastExecution()));
        m.put("nextExecution", iso(r.getNextExecution()));
        m.put("nextActualExecution", iso(r.getNextActualExecution()));
        m.put("resumeDate", iso(r.getResumeDate()));
        m.put("cronExpressions", new ArrayList<String>(r.getCronExpressions()));
        m.put("arguments", new LinkedHashMap<String, Object>(r.getArguments()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
