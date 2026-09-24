package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeAuditEventExtractionResult;
import com.keyforge.nativeiiq.model.NativeAuditEventRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON envelope for native AuditEvent rows, carrying {@code sourceCount} for a full-scan guard. */
public final class NativeAuditEventWire {

    private NativeAuditEventWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "AuditEvent");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeAuditEventExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeAuditEventRow r : result.getAuditEvents()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "AuditEvent");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeAuditEventRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("action", r.getAction());
        m.put("auditSource", r.getAuditSource());
        m.put("target", r.getTarget());
        m.put("application", r.getApplication());
        m.put("accountName", r.getAccountName());
        m.put("instance", r.getInstance());
        m.put("attributeName", r.getAttributeName());
        m.put("attributeValue", r.getAttributeValue());
        m.put("interfaceName", r.getInterfaceName());
        m.put("serverHost", r.getServerHost());
        m.put("clientHost", r.getClientHost());
        m.put("trackingId", r.getTrackingId());
        m.put("string1", r.getString1());
        m.put("string2", r.getString2());
        m.put("string3", r.getString3());
        m.put("string4", r.getString4());
        m.put("attributes", new LinkedHashMap<String, Object>(r.getAttributes()));
        m.put("created", iso(r.getCreated()));
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
