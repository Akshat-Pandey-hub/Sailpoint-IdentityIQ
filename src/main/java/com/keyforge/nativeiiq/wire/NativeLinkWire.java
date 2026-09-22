package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeLinkExtractionResult;
import com.keyforge.nativeiiq.model.NativeLinkRow;
import com.keyforge.nativeiiq.model.NativePermissionRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeLinkExtractionResult} into a plain JSON-friendly structure (only Maps, Lists,
 * Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep) but
 * bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeLinkFields}.
 */
public final class NativeLinkWire {

    private NativeLinkWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Link");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeLinkExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeLinkRow r : result.getLinks()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Link");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeLinkRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("uuid", r.getUuid());
        m.put("nativeIdentity", r.getNativeIdentity());
        m.put("displayName", r.getDisplayName());
        m.put("displayableName", r.getDisplayableName());
        m.put("instance", r.getInstance());
        m.put("componentIds", r.getComponentIds());
        m.put("applicationId", r.getApplicationId());
        m.put("applicationName", r.getApplicationName());
        m.put("identityId", r.getIdentityId());
        m.put("identityName", r.getIdentityName());
        m.put("disabled", r.getDisabled());
        m.put("locked", r.getLocked());
        m.put("composite", r.getComposite());
        m.put("manuallyCorrelated", r.getManuallyCorrelated());
        m.put("hasEntitlements", r.getHasEntitlements());
        m.put("iiqDisabled", r.getIiqDisabled());
        m.put("iiqLocked", r.getIiqLocked());
        m.put("permissions", permissions(r.getPermissions()));
        m.put("targetPermissions", permissions(r.getTargetPermissions()));
        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));
        m.put("entitlementAttributes", new LinkedHashMap<String, Object>(r.getEntitlementAttributes()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("lastRefresh", iso(r.getLastRefresh()));
        m.put("lastTargetAggregation", iso(r.getLastTargetAggregation()));
        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static List<Map<String, Object>> permissions(List<NativePermissionRef> perms) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (NativePermissionRef p : perms) {
            Map<String, Object> pm = new LinkedHashMap<String, Object>();
            pm.put("target", p.getTarget());
            pm.put("rights", p.getRights());
            pm.put("annotation", p.getAnnotation());
            out.add(pm);
        }
        return out;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
