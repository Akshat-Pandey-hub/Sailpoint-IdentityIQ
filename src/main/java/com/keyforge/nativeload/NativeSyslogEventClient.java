package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native SyslogEvent payload from the KeyForge plugin over the authenticated IIQ web session.
 * Endpoint: {@code plugin/rest/keyForgeNativeIIQ/syslog-events?start=&limit=}.
 */
public final class NativeSyslogEventClient implements NativeSyslogEventPageSource {

    public static final String SYSLOG_EVENTS_PATH = "plugin/rest/keyForgeNativeIIQ/syslog-events";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativeSyslogEventClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Sets the optional incremental bound (ISO-8601 instant). When set, every page request carries
     * {@code &modifiedAfter=}, so the IIQ plugin returns only objects created after it (these objects are
     * append-only; server-side {@code Filter.gt("created", ...)}). {@code null}/blank clears it (full scan).
     */
    public NativeSyslogEventClient withModifiedAfter(String modifiedAfter) {
        this.modifiedAfter = (modifiedAfter == null || modifiedAfter.isBlank()) ? null : modifiedAfter.trim();
        return this;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        if (modifiedAfter != null) {
            params.put("modifiedAfter", modifiedAfter); // server-side incremental bound (created >)
        }
        return session.get(SYSLOG_EVENTS_PATH, params);
    }
}
