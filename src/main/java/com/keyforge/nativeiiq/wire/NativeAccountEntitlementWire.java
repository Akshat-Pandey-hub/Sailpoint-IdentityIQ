package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeAccountEntitlementExtractionResult;
import com.keyforge.nativeiiq.model.NativeAccountEntitlementRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeAccountEntitlementExtractionResult} into a plain JSON-friendly structure. Emits
 * both {@code sourceCount} (total Links) and {@code returnedLinks} (Links covered by this page) so the
 * importer pages by Link and guards against an incomplete scan. Field names are the wire contract consumed
 * by {@code com.keyforge.nativeload.NativeAccountEntitlementFields}.
 */
public final class NativeAccountEntitlementWire {

    private NativeAccountEntitlementWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "AccountEntitlement");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeAccountEntitlementExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeAccountEntitlementRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "AccountEntitlement");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("returnedLinks", Integer.valueOf(result == null ? 0 : result.getLinkCount()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeAccountEntitlementRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("linkId", r.getLinkId());
        m.put("identityId", r.getIdentityId());
        m.put("identityName", r.getIdentityName());
        m.put("applicationId", r.getApplicationId());
        m.put("applicationName", r.getApplicationName());
        m.put("nativeIdentity", r.getNativeIdentity());
        m.put("instance", r.getInstance());
        m.put("attributeName", r.getAttributeName());
        m.put("attributeValue", r.getAttributeValue());
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
