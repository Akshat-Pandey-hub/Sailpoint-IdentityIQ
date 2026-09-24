package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native AuditEvent payload from the KeyForge plugin over the authenticated IIQ web session.
 * Endpoint: {@code plugin/rest/keyForgeNativeIIQ/audit-events?start=&limit=}.
 */
public final class NativeAuditEventClient implements NativeAuditEventPageSource {

    public static final String AUDIT_EVENTS_PATH = "plugin/rest/keyForgeNativeIIQ/audit-events";

    private final IiqSessionClient session;

    public NativeAuditEventClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(AUDIT_EVENTS_PATH, params);
    }
}
