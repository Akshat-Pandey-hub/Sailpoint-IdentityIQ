package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native ProvisioningTransaction payload from the KeyForge plugin's REST endpoint over the
 * authenticated IIQ web session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/provisioning-transactions}.
 */
public final class NativeProvisioningTxnClient implements NativeProvisioningTxnPageSource {

    public static final String PROVISIONING_TRANSACTIONS_PATH = "plugin/rest/keyForgeNativeIIQ/provisioning-transactions";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativeProvisioningTxnClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Sets the optional incremental bound (ISO-8601 instant). When set, every page request carries
     * {@code &modifiedAfter=}, so the IIQ plugin returns only objects changed after it (server-side: modified after
     * it, or never modified and created after it). {@code null}/blank clears it (full scan). Returns {@code this}.
     */
    public NativeProvisioningTxnClient withModifiedAfter(String modifiedAfter) {
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
        return session.get(PROVISIONING_TRANSACTIONS_PATH, params);
    }
}
