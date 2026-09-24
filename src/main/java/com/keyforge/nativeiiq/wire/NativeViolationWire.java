package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeViolationExtractionResult;
import com.keyforge.nativeiiq.model.NativeViolationRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON envelope for native PolicyViolation rows. Field names are the contract for the loader. */
public final class NativeViolationWire {

    private NativeViolationWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "PolicyViolation");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeViolationExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeViolationRow r : result.getViolations()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "PolicyViolation");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeViolationRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("identityId", r.getIdentityId());
        m.put("identityName", r.getIdentityName());
        m.put("policyId", r.getPolicyId());
        m.put("policyName", r.getPolicyName());
        m.put("constraintId", r.getConstraintId());
        m.put("constraintName", r.getConstraintName());
        m.put("status", r.getStatus());
        m.put("active", r.getActive());
        m.put("leftBundles", r.getLeftBundles());
        m.put("rightBundles", r.getRightBundles());
        m.put("entitlementsMarkedForRemediation", r.getEntitlementsMarkedForRemediation());
        m.put("bundlesMarkedForRemediation", r.getBundlesMarkedForRemediation());
        m.put("relevantApps", new ArrayList<Object>(r.getRelevantApps()));
        m.put("violatingEntitlements", new ArrayList<Object>(r.getViolatingEntitlements()));
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
