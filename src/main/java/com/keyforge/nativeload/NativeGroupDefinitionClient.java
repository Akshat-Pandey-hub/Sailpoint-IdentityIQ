package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native GroupDefinition payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/group-definitions?start=&limit=} (relative to {@code IIQ_BASE_URL}).
 */
public final class NativeGroupDefinitionClient implements NativeGroupDefinitionPageSource {

    /** Plugin REST path (relative to IIQ base). Matches the resource's {@code @Path}. */
    public static final String GROUP_DEFINITIONS_PATH = "plugin/rest/keyForgeNativeIIQ/group-definitions";

    private final IiqSessionClient session;

    public NativeGroupDefinitionClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(GROUP_DEFINITIONS_PATH, params);
    }
}
