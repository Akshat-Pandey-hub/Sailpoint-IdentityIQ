package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeRoleExtractionResult;
import com.keyforge.nativeiiq.model.NativeRoleRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeRoleExtractionResult} into a plain JSON-friendly structure (only Maps, Lists,
 * Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep) but
 * bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeRoleFields}.
 */
public final class NativeRoleWire {

    private NativeRoleWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Bundle");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeRoleExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeRoleRow r : result.getRoles()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Bundle");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeRoleRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("displayName", r.getDisplayName());
        m.put("displayableName", r.getDisplayableName());
        m.put("fullName", r.getFullName());
        m.put("description", r.getDescription());
        m.put("type", r.getType());
        m.put("assignmentId", r.getAssignmentId());
        m.put("activityEnabled", r.getActivityEnabled());
        m.put("allowDuplicateAccounts", r.getAllowDuplicateAccounts());
        m.put("allowMultipleAssignments", r.getAllowMultipleAssignments());
        m.put("autoPromotion", r.getAutoPromotion());
        m.put("differencable", r.getDifferencable());
        m.put("iiqElevatedAccess", r.getIiqElevatedAccess());
        m.put("mergeTemplates", r.getMergeTemplates());
        m.put("orProfiles", r.getOrProfiles());
        m.put("pendingDelete", r.getPendingDelete());
        m.put("hasSelector", r.getHasSelector());
        m.put("riskScoreWeight", r.getRiskScoreWeight());
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("activationDate", iso(r.getActivationDate()));
        m.put("deactivationDate", iso(r.getDeactivationDate()));
        m.put("descriptions", new LinkedHashMap<String, String>(r.getDescriptions()));
        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));
        m.put("roleTypeDefinition", r.getRoleTypeDefinition());
        m.put("applications", r.getApplications());
        m.put("monitoredApplications", r.getMonitoredApplications());
        m.put("scorecard", r.getScorecard());
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
