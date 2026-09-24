package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeIdentityRoleExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityRoleRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON wire envelope for native identity-role edges (pages over Identity). */
public final class NativeIdentityRoleWire {

    private NativeIdentityRoleWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "IdentityRole");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeIdentityRoleExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeIdentityRoleRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "IdentityRole");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("returnedIdentities", Integer.valueOf(result == null ? 0 : result.getIdentityCount()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeIdentityRoleRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("identityId", r.getIdentityId());
        m.put("identityName", r.getIdentityName());
        m.put("roleId", r.getRoleId());
        m.put("roleName", r.getRoleName());
        m.put("relationshipType", r.getRelationshipType());
        m.put("assignmentId", r.getAssignmentId());
        m.put("detectionAssignmentIds", r.getDetectionAssignmentIds());
        m.put("comments", r.getComments());
        m.put("futureAssignment", r.getFutureAssignment());
        m.put("promotedSoftPermit", r.getPromotedSoftPermit());
        m.put("detectionDate", iso(r.getDetectionDate()));
        m.put("targets", new ArrayList<Map<String, Object>>(r.getTargets()));
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
