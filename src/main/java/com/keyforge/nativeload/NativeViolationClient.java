package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native PolicyViolation payload from the KeyForge plugin over the authenticated IIQ web
 * session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/violations?start=&limit=}.
 */
public final class NativeViolationClient implements NativeViolationPageSource {

    public static final String VIOLATIONS_PATH = "plugin/rest/keyForgeNativeIIQ/violations";

    private final IiqSessionClient session;

    public NativeViolationClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(VIOLATIONS_PATH, params);
    }
}
