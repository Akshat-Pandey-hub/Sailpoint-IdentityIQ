package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native IdentityEntitlement payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/identity-entitlements?start=&limit=} (relative to {@code IIQ_BASE_URL}).
 */
public final class NativeIdentityEntitlementClient implements NativeIdentityEntitlementPageSource {

    public static final String IDENTITY_ENTITLEMENTS_PATH = "plugin/rest/keyForgeNativeIIQ/identity-entitlements";

    private final IiqSessionClient session;

    public NativeIdentityEntitlementClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(IDENTITY_ENTITLEMENTS_PATH, params);
    }
}
