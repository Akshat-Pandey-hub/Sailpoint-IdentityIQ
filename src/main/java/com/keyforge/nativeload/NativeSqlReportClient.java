package com.keyforge.nativeload;

import com.keyforge.iiq.client.IiqSessionClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls a baked SQL-report result from the KeyForge plugin's REST endpoint over the same authenticated
 * IdentityIQ web session the rest of this tool uses ({@link IiqSessionClient}). The plugin runs the
 * business SQL inside IIQ via {@code SailPointContext.getJdbcConnection()} and returns the rows as JSON;
 * this client only transports that JSON out. No new credentials, no direct SailPoint-DB access.
 *
 * <p>Endpoint: {@code plugin/rest/keyForgeNativeIIQ/sqlReport/{report}} (relative to {@code IIQ_BASE_URL}).
 */
public final class NativeSqlReportClient {

    /** Plugin REST path prefix (relative to IIQ base). Matches {@code NativeSqlReportResource @Path}. */
    public static final String SQL_REPORT_PATH = "plugin/rest/keyForgeNativeIIQ/sqlReport/";

    private final IiqSessionClient session;

    public NativeSqlReportClient(IiqSessionClient session) {
        this.session = session;
    }

    /**
     * Fetches one JSON page from a report. Filters, start, and limit are sent to the plugin so that
     * filtering remains before paging and the plugin does not return the full report body.
     *
     * @param report the server-registered report name (e.g. {@code entitlementAssignment})
     * @return the raw JSON envelope body
     */
    public String fetch(String report, Map<String, String> filters, Integer start, Integer limit) {
        return fetch(report, filters, start, limit, null);
    }

    /** Fetches a page, optionally bounding source-row change times before the plugin pages it. */
    public String fetch(String report, Map<String, String> filters, Integer start, Integer limit,
                        String modifiedAfter) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<String, String>();
        if (filters != null) {
            params.putAll(filters);
        }
        params.remove("modifiedAfter"); // reserved control parameter, never a report-column filter
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            params.put("modifiedAfter", modifiedAfter.trim());
        }
        if (start != null) {
            params.put("start", start.toString());
        }
        if (limit != null) {
            params.put("limit", limit.toString());
        }
        return session.get(SQL_REPORT_PATH + report, params);
    }

    /** Backward-compatible convenience for callers that need the legacy unpaged envelope. */
    public String fetch(String report) {
        return fetch(report, null, null, null);
    }
}
