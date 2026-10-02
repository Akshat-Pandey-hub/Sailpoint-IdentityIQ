package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native Application payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/applications?start=&limit=} (relative to {@code IIQ_BASE_URL}).
 */
public final class NativeApplicationClient implements NativeApplicationPageSource {

    /** Plugin REST path (relative to IIQ base). Matches the resource's {@code @Path}. */
    public static final String APPLICATIONS_PATH = "plugin/rest/keyForgeNativeIIQ/applications";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativeApplicationClient(IiqSessionClient session) {
        this.session = session;
    }

    /** Sets the optional CSS incremental bound (ISO-8601). When set, each page carries {@code modifiedAfter}. */
    public NativeApplicationClient withModifiedAfter(String modifiedAfter) {
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
            params.put("modifiedAfter", modifiedAfter);
        }
        return session.get(APPLICATIONS_PATH, params);
    }
}
