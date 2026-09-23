package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native Certification payload from the KeyForge plugin's REST endpoint over the authenticated
 * IIQ web session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/certifications?start=&limit=}.
 */
public final class NativeCertificationClient implements NativeCertificationPageSource {

    public static final String CERTIFICATIONS_PATH = "plugin/rest/keyForgeNativeIIQ/certifications";

    private final IiqSessionClient session;

    public NativeCertificationClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(CERTIFICATIONS_PATH, params);
    }
}
