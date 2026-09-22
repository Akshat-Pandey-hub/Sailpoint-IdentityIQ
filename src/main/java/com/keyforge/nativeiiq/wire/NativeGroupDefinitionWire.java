package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeGroupDefinitionExtractionResult;
import com.keyforge.nativeiiq.model.NativeGroupDefinitionRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeGroupDefinitionExtractionResult} into a plain JSON-friendly structure (only
 * Maps, Lists, Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint
 * dep) but bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeGroupDefinitionFields}.
 */
public final class NativeGroupDefinitionWire {

    private NativeGroupDefinitionWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "GroupDefinition");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeGroupDefinitionExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeGroupDefinitionRow r : result.getGroupDefinitions()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "GroupDefinition");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeGroupDefinitionRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("type", r.getType());
        m.put("factoryId", r.getFactoryId());
        m.put("factoryName", r.getFactoryName());
        m.put("filterExpression", r.getFilterExpression());
        m.put("isPrivate", r.getIsPrivate());
        m.put("indexed", r.getIndexed());
        m.put("nullGroup", r.getNullGroup());
        m.put("nameUnique", r.getNameUnique());
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("lastRefresh", iso(r.getLastRefresh()));
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
