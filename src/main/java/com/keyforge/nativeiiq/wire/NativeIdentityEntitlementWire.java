package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeIdentityEntitlementExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityEntitlementRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeIdentityEntitlementExtractionResult} into a plain JSON-friendly structure (only
 * Maps, Lists, Strings, Booleans, Numbers). Pure Java (no SailPoint dep) but bundled in the plugin. Field
 * names are the wire contract consumed by {@code com.keyforge.nativeload.NativeIdentityEntitlementFields}.
 */
public final class NativeIdentityEntitlementWire {

    private NativeIdentityEntitlementWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "IdentityEntitlement");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeIdentityEntitlementExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeIdentityEntitlementRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "IdentityEntitlement");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeIdentityEntitlementRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("identityId", r.getIdentityId());
        m.put("identityName", r.getIdentityName());
        m.put("applicationId", r.getApplicationId());
        m.put("applicationName", r.getApplicationName());
        m.put("nativeIdentity", r.getNativeIdentity());
        m.put("instance", r.getInstance());
        m.put("attributeName", r.getAttributeName());
        m.put("attributeValue", r.getAttributeValue());
        m.put("valueList", new ArrayList<String>(r.getValueList()));
        m.put("type", r.getType());
        m.put("displayName", r.getDisplayName());
        m.put("annotation", r.getAnnotation());
        m.put("assigned", r.getAssigned());
        m.put("grantedByRole", r.getGrantedByRole());
        m.put("allowed", r.getAllowed());
        m.put("connected", r.getConnected());
        m.put("aggregationState", r.getAggregationState());
        m.put("source", r.getSource());
        m.put("sourceObject", r.getSourceObject());
        m.put("assigner", r.getAssigner());
        m.put("assignmentId", r.getAssignmentId());
        m.put("assignmentNote", r.getAssignmentNote());
        m.put("sourceAssignableRoles", r.getSourceAssignableRoles());
        m.put("sourceDetectedRoles", r.getSourceDetectedRoles());
        m.put("certificationItemId", r.getCertificationItemId());
        m.put("pendingCertificationItemId", r.getPendingCertificationItemId());
        m.put("requestItemId", r.getRequestItemId());
        m.put("pendingRequestItemId", r.getPendingRequestItemId());
        m.put("startDate", iso(r.getStartDate()));
        m.put("endDate", iso(r.getEndDate()));
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
