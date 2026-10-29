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
    private String modifiedAfter;

    public NativeCertificationEntityClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Sets the optional incremental bound (ISO-8601 instant). When set, every page request carries
     * {@code &modifiedAfter=}, so the IIQ plugin returns only objects changed after it (server-side: modified after
     * it, or never modified and created after it). {@code null}/blank clears it (full scan). Returns {@code this}.
     */
    public NativeCertificationEntityClient withModifiedAfter(String modifiedAfter) {
        this.modifiedAfter = (modifiedAfter == null || modifiedAfter.isBlank()) ? null : modifiedAfter.trim();
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
        if (modifiedAfter != null) {
            params.put("modifiedAfter", modifiedAfter); // server-side incremental bound
        }
        if (includeCount) {
            params.put("includeCount", "true"); // the plugin counts only when asked
        }
        return session.get(CERTIFICATION_ENTITIES_PATH, params);
    }
}
