package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native IdentityRequest payload from the KeyForge plugin's REST endpoint over the authenticated
 * IIQ web session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/identity-requests}.
 */
public final class NativeIdentityRequestClient implements NativeIdentityRequestPageSource {

    public static final String IDENTITY_REQUESTS_PATH = "plugin/rest/keyForgeNativeIIQ/identity-requests";

    private final IiqSessionClient session;

    public NativeIdentityRequestClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(IDENTITY_REQUESTS_PATH, params);
    }
}
