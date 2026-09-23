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

    public NativeProvisioningTxnClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(PROVISIONING_TRANSACTIONS_PATH, params);
    }
}
