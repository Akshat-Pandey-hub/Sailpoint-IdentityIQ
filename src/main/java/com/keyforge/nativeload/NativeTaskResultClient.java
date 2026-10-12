package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native TaskResult payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/task-results?start=&limit=} (relative to {@code IIQ_BASE_URL}).
 */
public final class NativeTaskResultClient implements NativeTaskResultPageSource {

    /** Plugin REST path (relative to IIQ base). Matches the resource's {@code @Path}. */
    public static final String TASK_RESULTS_PATH = "plugin/rest/keyForgeNativeIIQ/task-results";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativeTaskResultClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Sets the optional incremental bound (ISO-8601 instant). When set, every page request carries
     * {@code &modifiedAfter=}, so the IIQ plugin returns only objects changed after it (server-side: modified after
     * it, or never modified and created after it). {@code null}/blank clears it (full scan). Returns {@code this}.
     */
    public NativeTaskResultClient withModifiedAfter(String modifiedAfter) {
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
        return session.get(TASK_RESULTS_PATH, params);
    }
}
