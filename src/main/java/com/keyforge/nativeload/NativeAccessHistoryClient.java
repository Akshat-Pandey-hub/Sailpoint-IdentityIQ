package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the native Access-History payloads from the KeyForge plugin over the same authenticated IIQ web
 * session the rest of this tool uses ({@link IiqSessionClient}). No new credentials or DB access — the
 * plugin runs the native SailPoint Java API inside IIQ and returns the rows as JSON. One client, four
 * read-only endpoints under {@code plugin/rest/keyForgeNativeIIQ/access-history/*}.
 */
public final class NativeAccessHistoryClient {

    public static final String ENTITLEMENT_CAPTURES_PATH =
            "plugin/rest/keyForgeNativeIIQ/access-history/entitlement-captures";
    public static final String IDENTITY_EVENTS_PATH =
            "plugin/rest/keyForgeNativeIIQ/access-history/identity-events";
    public static final String ROLE_EVENTS_PATH =
            "plugin/rest/keyForgeNativeIIQ/access-history/role-events";
    public static final String CERTIFICATIONS_PATH =
            "plugin/rest/keyForgeNativeIIQ/access-history/certifications";

    private final IiqSessionClient session;

    public NativeAccessHistoryClient(IiqSessionClient session) {
        this.session = session;
    }

    /** Fetches one page of the given Access-History endpoint (raw JSON envelope body). */
    public String fetchPage(String path, int start, int limit) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        return session.get(path, params);
    }
}
