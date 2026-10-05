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
     * Fetches the full JSON envelope for a report (the server returns the whole result set in one
     * response). Warms the CSRF token first, exactly as the other native reads do.
     *
     * @param report the server-registered report name (e.g. {@code entitlementAssignment})
     * @return the raw JSON envelope body
     */
    public String fetch(String report) {
        session.warmCsrfToken();
        Map<String, String> params = new LinkedHashMap<String, String>();
        return session.get(SQL_REPORT_PATH + report, params);
    }
}
