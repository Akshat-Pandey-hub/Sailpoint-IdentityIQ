package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeWorkgroupExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkgroupRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeWorkgroupExtractionResult} into a plain JSON-friendly structure (only Maps,
 * Lists, Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep)
 * but bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeWorkgroupFields}.
 */
public final class NativeWorkgroupWire {

    private NativeWorkgroupWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Workgroup");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeWorkgroupExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeWorkgroupRow r : result.getWorkgroups()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Workgroup");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeWorkgroupRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("displayName", r.getDisplayName());
        m.put("displayableName", r.getDisplayableName());
        m.put("email", r.getEmail());
        m.put("type", r.getType());
        m.put("description", r.getDescription());
        m.put("notificationOption", r.getNotificationOption());
        m.put("inactive", r.getInactive());
        m.put("workgroup", r.getWorkgroup());
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("capabilities", new ArrayList<String>(r.getCapabilities()));
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
