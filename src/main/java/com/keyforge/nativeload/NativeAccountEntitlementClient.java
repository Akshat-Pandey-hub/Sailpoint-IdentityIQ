package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native account-entitlement payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/account-entitlements?start=&limit=} (relative to {@code IIQ_BASE_URL}).
 * The {@code start}/{@code limit} window pages over {@code Link}s.
 */
public final class NativeAccountEntitlementClient implements NativeAccountEntitlementPageSource {

    public static final String ACCOUNT_ENTITLEMENTS_PATH = "plugin/rest/keyForgeNativeIIQ/account-entitlements";

    private final IiqSessionClient session;

    public NativeAccountEntitlementClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(ACCOUNT_ENTITLEMENTS_PATH, params);
    }
}
