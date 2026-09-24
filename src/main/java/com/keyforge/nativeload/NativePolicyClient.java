package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native Policy payload from the KeyForge plugin over the authenticated IIQ web session.
 * Endpoint: {@code plugin/rest/keyForgeNativeIIQ/policies?start=&limit=}.
 */
public final class NativePolicyClient implements NativePolicyPageSource {

    public static final String POLICIES_PATH = "plugin/rest/keyForgeNativeIIQ/policies";

    private final IiqSessionClient session;

    public NativePolicyClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(POLICIES_PATH, params);
    }
}
