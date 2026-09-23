package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeApplicationExtractionResult;
import com.keyforge.nativeiiq.model.NativeApplicationRow;
import com.keyforge.nativeiiq.model.NativeReferenceRef;
import com.keyforge.nativeiiq.model.NativeSchemaRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeApplicationExtractionResult} into a plain JSON-friendly structure (only Maps,
 * Lists, Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep)
 * but bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeApplicationFields}.
 */
public final class NativeApplicationWire {

    private NativeApplicationWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Application");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeApplicationExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeApplicationRow r : result.getApplications()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Application");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeApplicationRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("type", r.getType());
        m.put("connector", r.getConnector());
        m.put("featuresString", r.getFeaturesString());
        m.put("profileClass", r.getProfileClass());
        m.put("proxiedName", r.getProxiedName());
        m.put("cluster", r.getCluster());
        m.put("icon", r.getIcon());
        m.put("aggregationTypes", r.getAggregationTypes());
        m.put("beforeProvisioningRule", r.getBeforeProvisioningRule());
        m.put("afterProvisioningRule", r.getAfterProvisioningRule());
        m.put("accountSchemaCorrelationRule", r.getAccountSchemaCorrelationRule());
        m.put("accountSchemaCustomizationRule", r.getAccountSchemaCustomizationRule());
        m.put("accountSchemaCreationRule", r.getAccountSchemaCreationRule());
        m.put("accountSchemaRefreshRule", r.getAccountSchemaRefreshRule());
        m.put("applicationCreationRule", r.getApplicationCreationRule());
        m.put("accountSchemaCorrelationRuleId", r.getAccountSchemaCorrelationRuleId());
        m.put("accountSchemaCustomizationRuleId", r.getAccountSchemaCustomizationRuleId());
        m.put("accountSchemaCreationRuleId", r.getAccountSchemaCreationRuleId());
        m.put("accountSchemaRefreshRuleId", r.getAccountSchemaRefreshRuleId());
        m.put("applicationCreationRuleId", r.getApplicationCreationRuleId());
        m.put("score", r.getScore());
        m.put("authoritative", r.getAuthoritative());
        m.put("caseInsensitive", r.getCaseInsensitive());
        m.put("logical", r.getLogical());
        m.put("composite", r.getComposite());
        m.put("authenticationResource", r.getAuthenticationResource());
        m.put("activityEnabled", r.getActivityEnabled());
        m.put("inMaintenance", r.getInMaintenance());
        m.put("managesOtherApps", r.getManagesOtherApps());
        m.put("nativeChangeDetectionEnabled", r.getNativeChangeDetectionEnabled());
        m.put("supportsProvisioning", r.getSupportsProvisioning());
        m.put("supportsAccountOnly", r.getSupportsAccountOnly());
        m.put("supportsAdditionalAccounts", r.getSupportsAdditionalAccounts());
        m.put("supportsAuthenticate", r.getSupportsAuthenticate());
        m.put("supportsGroupProvisioning", r.getSupportsGroupProvisioning());
        m.put("supportsDirectPermissions", r.getSupportsDirectPermissions());
        m.put("syncProvisioning", r.getSyncProvisioning());
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("secondaryOwners", refs(r.getSecondaryOwners()));
        m.put("remediators", refs(r.getRemediators()));
        m.put("dependencies", refs(r.getDependencies()));

        List<Map<String, Object>> schemas = new ArrayList<Map<String, Object>>();
        for (NativeSchemaRef s : r.getSchemas()) {
            Map<String, Object> sm = new LinkedHashMap<String, Object>();
            sm.put("objectType", s.getObjectType());
            sm.put("nativeObjectType", s.getNativeObjectType());
            sm.put("identityAttribute", s.getIdentityAttribute());
            sm.put("displayAttribute", s.getDisplayAttribute());
            sm.put("instanceAttribute", s.getInstanceAttribute());
            sm.put("attributeCount", s.getAttributeCount());
            schemas.add(sm);
        }
        m.put("schemas", schemas);

        m.put("descriptions", new LinkedHashMap<String, String>(r.getDescriptions()));
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

    private static List<Map<String, Object>> refs(List<NativeReferenceRef> refs) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (NativeReferenceRef ref : refs) {
            Map<String, Object> rm = new LinkedHashMap<String, Object>();
            rm.put("id", ref.getId());
            rm.put("name", ref.getName());
            out.add(rm);
        }
        return out;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
