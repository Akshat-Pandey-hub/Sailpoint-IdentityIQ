package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeSyslogEventExtractionResult;
import com.keyforge.nativeiiq.model.NativeSyslogEventRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON envelope for native SyslogEvent rows, carrying {@code sourceCount} for a full-scan guard. */
public final class NativeSyslogEventWire {

    private NativeSyslogEventWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "SyslogEvent");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeSyslogEventExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeSyslogEventRow r : result.getSyslogEvents()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "SyslogEvent");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeSyslogEventRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("quickKey", r.getQuickKey());
        m.put("eventLevel", r.getEventLevel());
        m.put("server", r.getServer());
        m.put("username", r.getUsername());
        m.put("thread", r.getThread());
        m.put("lineNumber", r.getLineNumber());
        m.put("message", r.getMessage());
        m.put("stacktrace", r.getStacktrace());
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
