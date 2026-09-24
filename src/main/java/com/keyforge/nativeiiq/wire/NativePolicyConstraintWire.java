package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativePolicyConstraintExtractionResult;
import com.keyforge.nativeiiq.model.NativePolicyConstraintRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON envelope for native policy-constraint rows; carries sourceCount + returnedPolicies for the scan guard. */
public final class NativePolicyConstraintWire {

    private NativePolicyConstraintWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "PolicyConstraint");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativePolicyConstraintExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativePolicyConstraintRow r : result.getConstraints()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "PolicyConstraint");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("returnedPolicies", Integer.valueOf(result == null ? 0 : result.getPolicyCount()));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativePolicyConstraintRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("policyId", r.getPolicyId());
        m.put("policyName", r.getPolicyName());
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("constraintType", r.getConstraintType());
        m.put("weight", r.getWeight());
        m.put("compensatingControl", r.getCompensatingControl());
        m.put("violationOwnerId", r.getViolationOwnerId());
        m.put("violationOwnerName", r.getViolationOwnerName());
        m.put("violationOwnerType", r.getViolationOwnerType());
        m.put("leftBundles", new ArrayList<Map<String, Object>>(r.getLeftBundles()));
        m.put("rightBundles", new ArrayList<Map<String, Object>>(r.getRightBundles()));
        m.put("selectors", new ArrayList<Object>(r.getSelectors()));
        m.put("selectorCount", r.getSelectorCount());
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
