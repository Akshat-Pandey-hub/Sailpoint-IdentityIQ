package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native policy-constraint payload from the KeyForge plugin over the authenticated IIQ web
 * session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/policy-constraints?start=&limit=} (start/limit
 * page over the parent Policy).
 */
public final class NativePolicyConstraintClient implements NativePolicyConstraintPageSource {

    public static final String POLICY_CONSTRAINTS_PATH = "plugin/rest/keyForgeNativeIIQ/policy-constraints";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativePolicyConstraintClient(IiqSessionClient session) {
        this.session = session;
    }

    public NativePolicyConstraintClient withModifiedAfter(String value) {
        this.modifiedAfter = value == null || value.isBlank() ? null : value.trim();
        return this;
    }

    @Override
    public String fetchPage(int start, int limit) {
        return fetchPage(start, limit, false);
    }

    @Override
    public String fetchPage(int start, int limit, boolean includeCount) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        if (modifiedAfter != null) params.put("modifiedAfter", modifiedAfter);
        if (includeCount) {
            params.put("includeCount", "true"); // the plugin counts only when asked
        }
        return session.get(POLICY_CONSTRAINTS_PATH, params);
    }
}
