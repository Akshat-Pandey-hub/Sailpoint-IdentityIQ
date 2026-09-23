package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native CertificationEntity payload from the KeyForge plugin's REST endpoint over the authenticated
 * IIQ web session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/certification-entities?start=&limit=}.
 */
public final class NativeCertificationEntityClient implements NativeCertificationEntityPageSource {

    public static final String CERTIFICATION_ENTITIES_PATH = "plugin/rest/keyForgeNativeIIQ/certification-entities";

    private final IiqSessionClient session;

    public NativeCertificationEntityClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(CERTIFICATION_ENTITIES_PATH, params);
    }
}
