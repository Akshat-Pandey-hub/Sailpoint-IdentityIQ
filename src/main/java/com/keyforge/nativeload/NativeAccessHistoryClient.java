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
    private String modifiedAfter;

    public NativeAccessHistoryClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Sets the optional incremental bound (ISO-8601 instant). When set, every page request carries
     * {@code &modifiedAfter=}, so the IIQ plugin returns only objects created after it (these objects are
     * append-only; server-side {@code Filter.gt("created", ...)}). {@code null}/blank clears it (full scan).
     */
    public NativeAccessHistoryClient withModifiedAfter(String modifiedAfter) {
        this.modifiedAfter = (modifiedAfter == null || modifiedAfter.isBlank()) ? null : modifiedAfter.trim();
        return this;
    }

    /** Fetches one page of the given Access-History endpoint (raw JSON envelope body). */
    public String fetchPage(String path, int start, int limit) {
        return fetchPage(path, start, limit, false);
    }

    /** One page; asks the plugin to count the population only when {@code includeCount} is true. */
    public String fetchPage(String path, int start, int limit, boolean includeCount) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("start", Integer.toString(Math.max(0, start)));
        params.put("limit", Integer.toString(limit));
        if (modifiedAfter != null) {
            params.put("modifiedAfter", modifiedAfter); // server-side incremental bound (created >)
        }
        if (includeCount) {
            params.put("includeCount", "true"); // the plugin counts only when asked
        }
        return session.get(path, params);
    }
}
