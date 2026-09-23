package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native CertificationItem payload from the KeyForge plugin's REST endpoint over the
 * authenticated IIQ web session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/certification-items}.
 */
public final class NativeCertificationItemClient implements NativeCertificationItemPageSource {

    public static final String CERTIFICATION_ITEMS_PATH = "plugin/rest/keyForgeNativeIIQ/certification-items";

    private final IiqSessionClient session;

    public NativeCertificationItemClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(CERTIFICATION_ITEMS_PATH, params);
    }
}
