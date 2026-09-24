package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeTaskResultExtractionResult;
import com.keyforge.nativeiiq.model.NativeTaskResultRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeTaskResultExtractionResult} into a plain JSON-friendly structure (only Maps,
 * Lists, Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep)
 * but bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeTaskResultFields}.
 */
public final class NativeTaskResultWire {

    private NativeTaskResultWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "TaskResult");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeTaskResultExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeTaskResultRow r : result.getTaskResults()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "TaskResult");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeTaskResultRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("type", r.getType());
        m.put("completionStatus", r.getCompletionStatus());
        m.put("definitionName", r.getDefinitionName());
        m.put("launcher", r.getLauncher());
        m.put("host", r.getHost());
        m.put("targetName", r.getTargetName());
        m.put("targetClass", r.getTargetClass());
        m.put("targetId", r.getTargetId());
        m.put("schedule", r.getSchedule());
        m.put("progress", r.getProgress());
        m.put("percentComplete", r.getPercentComplete());
        m.put("runLength", r.getRunLength());
        m.put("pendingSignoffs", r.getPendingSignoffs());
        m.put("partitioned", r.getPartitioned());
        m.put("terminateRequested", r.getTerminateRequested());
        m.put("complete", r.getComplete());
        m.put("launched", iso(r.getLaunched()));
        m.put("completed", iso(r.getCompleted()));
        m.put("expiration", iso(r.getExpiration()));
        m.put("verified", iso(r.getVerified()));
        m.put("messages", new ArrayList<Map<String, Object>>(r.getMessages()));
        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));
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
