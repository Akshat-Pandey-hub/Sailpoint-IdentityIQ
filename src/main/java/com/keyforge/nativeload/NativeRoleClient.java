package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native Role (Bundle) payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/roles?start=&limit=} (relative to {@code IIQ_BASE_URL}).
 */
public final class NativeRoleClient implements NativeRolePageSource {

    /** Plugin REST path (relative to IIQ base). Matches the resource's {@code @Path}. */
    public static final String ROLES_PATH = "plugin/rest/keyForgeNativeIIQ/roles";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativeRoleClient(IiqSessionClient session) {
        this.session = session;
    }

    /** Sets the optional CSS incremental bound (ISO-8601). When set, each page carries {@code modifiedAfter}. */
    public NativeRoleClient withModifiedAfter(String modifiedAfter) {
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
        return session.get(ROLES_PATH, params);
    }
}
