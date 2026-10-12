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
    private String modifiedAfter;

    public NativeAccountEntitlementClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Sets the optional incremental bound (ISO-8601 instant). When set, every page request carries
     * {@code &modifiedAfter=}, so the IIQ plugin returns only objects changed after it (server-side: modified after
     * it, or never modified and created after it). {@code null}/blank clears it (full scan). Returns {@code this}.
     */
    public NativeAccountEntitlementClient withModifiedAfter(String modifiedAfter) {
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
            params.put("modifiedAfter", modifiedAfter); // server-side incremental bound
        }
        return session.get(ACCOUNT_ENTITLEMENTS_PATH, params);
    }
}
