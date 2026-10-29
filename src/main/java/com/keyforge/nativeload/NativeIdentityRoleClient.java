package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls native identity-role edges from the KeyForge plugin over the authenticated IIQ session. Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/identity-roles?start=&limit=}. The window pages over {@code Identity}.
 */
public final class NativeIdentityRoleClient implements NativeIdentityRolePageSource {

    public static final String IDENTITY_ROLES_PATH = "plugin/rest/keyForgeNativeIIQ/identity-roles";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativeIdentityRoleClient(IiqSessionClient session) {
        this.session = session;
    }

    public NativeIdentityRoleClient withModifiedAfter(String value) {
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
        return session.get(IDENTITY_ROLES_PATH, params);
    }
}
