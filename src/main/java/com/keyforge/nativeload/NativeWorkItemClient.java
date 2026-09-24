package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native WorkItem payload from the KeyForge plugin's REST endpoint over the authenticated
 * IIQ web session. Endpoint: {@code plugin/rest/keyForgeNativeIIQ/workitems?start=&limit=}.
 */
public final class NativeWorkItemClient implements NativeWorkItemPageSource {

    public static final String WORKITEMS_PATH = "plugin/rest/keyForgeNativeIIQ/workitems";

    private final IiqSessionClient session;

    public NativeWorkItemClient(IiqSessionClient session) {
        this.session = session;
    }

    @Override
    public String fetchPage(int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(WORKITEMS_PATH, params);
    }
}
