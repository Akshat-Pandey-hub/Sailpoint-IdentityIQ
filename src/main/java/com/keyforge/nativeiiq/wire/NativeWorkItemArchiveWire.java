package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeWorkItemArchiveExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkItemArchiveRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeWorkItemArchiveExtractionResult} into a plain JSON-friendly structure (only Maps,
 * Lists, Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep) but
 * bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeWorkItemArchiveFields}.
 */
public final class NativeWorkItemArchiveWire {

    private NativeWorkItemArchiveWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "WorkItemArchive");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeWorkItemArchiveExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeWorkItemArchiveRow r : result.getWorkItemArchives()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "WorkItemArchive");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? 0 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeWorkItemArchiveRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("workItemId", r.getWorkItemId());
        m.put("name", r.getName());
        m.put("type", r.getType());
        m.put("state", r.getState());
        m.put("level", r.getLevel());
        m.put("requester", r.getRequester());
        m.put("assignee", r.getAssignee());
        m.put("ownerName", r.getOwnerName());
        m.put("completer", r.getCompleter());
        m.put("completionComments", r.getCompletionComments());
        m.put("signed", r.getSigned());
        m.put("targetClass", r.getTargetClass());
        m.put("targetId", r.getTargetId());
        m.put("targetName", r.getTargetName());
        m.put("identityRequestId", r.getIdentityRequestId());
        m.put("certificationId", r.getCertificationId());
        m.put("certificationEntityId", r.getCertificationEntityId());
        m.put("certificationItemId", r.getCertificationItemId());
        m.put("entityType", r.getEntityType());
        m.put("signOffs", new ArrayList<Map<String, Object>>(r.getSignOffs()));
        m.put("comments", new ArrayList<Map<String, Object>>(r.getComments()));
        m.put("ownerHistory", new ArrayList<Map<String, Object>>(r.getOwnerHistory()));
        m.put("systemAttributes", new LinkedHashMap<String, Object>(r.getSystemAttributes()));
        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("expiration", iso(r.getExpiration()));
        m.put("archived", iso(r.getArchived()));
        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("srcNaturalKey", r.getSrcNaturalKey());
        m.put("srcEventTs", iso(r.getSrcEventTs()));
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
