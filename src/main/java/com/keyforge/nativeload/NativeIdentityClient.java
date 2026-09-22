package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native Identity payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}: JSF
 * login + session cookie + CSRF). No new credentials, network path or DB access — the plugin runs
 * inside IIQ and returns native Identity data as JSON.
 *
 * <p>Endpoint: {@code plugin/rest/keyForgeNativeIIQ/identities?start=&limit=} (relative to the
 * configured {@code IIQ_BASE_URL}).
 */
public final class NativeIdentityClient implements NativeIdentityPageSource {

    /** Plugin REST path (relative to IIQ base). Matches the resource's {@code @Path}. */
    public static final String IDENTITIES_PATH = "plugin/rest/keyForgeNativeIIQ/identities";

    private final IiqSessionClient session;

    public NativeIdentityClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Fetches one page of native Identity JSON. Warms the session CSRF token first, exactly as the
     * classic {@code ui/rest} reads do, so the authenticated GET is accepted.
     *
     * @return the raw JSON envelope body
     */
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(IDENTITIES_PATH, params);
    }
}
