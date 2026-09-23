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

    public NativeWorkItemArchiveClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(WORKITEM_ARCHIVES_PATH, params);
    }
}
