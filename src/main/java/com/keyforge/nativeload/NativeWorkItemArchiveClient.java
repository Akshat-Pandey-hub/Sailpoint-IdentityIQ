package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native WorkItemArchive payload from the KeyForge plugin's REST endpoint over the same
 * authenticated IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). Endpoint:
 * {@code plugin/rest/keyForgeNativeIIQ/workitem-archives?start=&limit=} (relative to {@code IIQ_BASE_URL}).
 */
public final class NativeWorkItemArchiveClient implements NativeWorkItemArchivePageSource {

    /** Plugin REST path (relative to IIQ base). Matches the resource's {@code @Path}. */
    public static final String WORKITEM_ARCHIVES_PATH = "plugin/rest/keyForgeNativeIIQ/workitem-archives";

    private final IiqSessionClient session;
    private String modifiedAfter;

    public NativeWorkItemArchiveClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Sets the optional incremental bound (ISO-8601 instant). When set, every page request carries
     * {@code &modifiedAfter=}, so the IIQ plugin returns only objects created after it (these objects are
     * append-only; server-side {@code Filter.gt("created", ...)}). {@code null}/blank clears it (full scan).
     */
    public NativeWorkItemArchiveClient withModifiedAfter(String modifiedAfter) {
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
            params.put("modifiedAfter", modifiedAfter); // server-side incremental bound (created >)
        }
        if (includeCount) {
            params.put("includeCount", "true"); // the plugin counts only when asked
        }
        return session.get(WORKITEM_ARCHIVES_PATH, params);
    }
}
