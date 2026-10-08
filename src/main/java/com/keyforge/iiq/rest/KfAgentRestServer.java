package com.keyforge.iiq.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.iiq.config.AppConfig;
import com.keyforge.nativeload.NativeAccountEntitlementClient;
import com.keyforge.nativeload.NativeAccountEntitlementPageSource;
import com.keyforge.nativeload.NativeAccountEntitlementRestService;
import com.keyforge.nativeload.NativeAccountRestService;
import com.keyforge.nativeload.NativeApplicationClient;
import com.keyforge.nativeload.NativeApplicationPageSource;
import com.keyforge.nativeload.NativeApplicationRestService;
import com.keyforge.nativeload.NativeEntitlementRestService;
import com.keyforge.nativeload.NativeLinkClient;
import com.keyforge.nativeload.NativeLinkPageSource;
import com.keyforge.nativeload.NativeIdentityClient;
import com.keyforge.nativeload.NativeIdentityPageSource;
import com.keyforge.nativeload.NativeIdentityEntitlementClient;
import com.keyforge.nativeload.NativeIdentityEntitlementPageSource;
import com.keyforge.nativeload.NativeIdentityEntitlementRestService;
import com.keyforge.nativeload.NativeIdentityRequestApprovalRestService;
import com.keyforge.nativeload.NativeIdentityRequestClient;
import com.keyforge.nativeload.NativeIdentityRequestItemRestService;
import com.keyforge.nativeload.NativeIdentityRequestPageSource;
import com.keyforge.nativeload.NativeIdentityRequestRestService;
import com.keyforge.nativeload.NativeIdentityRestService;
import com.keyforge.nativeload.NativeIdentityRoleClient;
import com.keyforge.nativeload.NativeIdentityRolePageSource;
import com.keyforge.nativeload.NativeIdentityRoleRestService;
import com.keyforge.nativeload.NativeManagedAttributeClient;
import com.keyforge.nativeload.NativeManagedAttributePageSource;
import com.keyforge.nativeload.NativePolicyClient;
import com.keyforge.nativeload.NativePolicyPageSource;
import com.keyforge.nativeload.NativePolicyRestService;
import com.keyforge.nativeload.NativeProvisioningItemRestService;
import com.keyforge.nativeload.NativeProvisioningTxnClient;
import com.keyforge.nativeload.NativeProvisioningTxnPageSource;
import com.keyforge.nativeload.NativeRoleClient;
import com.keyforge.nativeload.NativeRoleEntitlementRestService;
import com.keyforge.nativeload.NativeRoleRelationshipClient;
import com.keyforge.nativeload.NativeRoleRelationshipPageSource;
import com.keyforge.nativeload.NativeRolePageSource;
import com.keyforge.nativeload.NativeRoleRestService;
import com.keyforge.nativeload.NativeWorkgroupClient;
import com.keyforge.nativeload.NativeWorkgroupPageSource;
import com.keyforge.nativeload.NativeWorkgroupRestService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * KF Agent REST service — exposes the existing native (Java-API) extraction as read-only JSON over HTTP,
 * so an external consumer (the Assurance Tool) can pull SailPoint data without any client-side database.
 * It reuses the existing native extraction/mapping verbatim and persists nothing: the DB write path is
 * bypassed here (see {@link NativeEntitlementRestService}). SCIM / {@code IiqApiClient} is NOT involved.
 *
 * <p>Resource-oriented endpoints (first module implemented): {@code GET /kfagent/entitlements}. Later
 * modules will be added as {@code /kfagent/identities}, {@code /kfagent/applications}, etc.
 *
 * <p>Default bind {@code 0.0.0.0:8100} (so it is reachable through an ngrok tunnel); override with env
 * {@code KFAGENT_HOST} / {@code KFAGENT_PORT} (falls back to {@code REST_PORT}, default 8100).
 */
public final class KfAgentRestServer {

    public static final String PREFIX = "/kfagent";
    public static final int DEFAULT_PORT = 8100;

    private final String host;
    private final int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final IiqSessionClient session;
    private final NativeEntitlementRestService entitlementService = new NativeEntitlementRestService();
    private final NativeIdentityRestService identityService = new NativeIdentityRestService();
    private final NativeApplicationRestService applicationService = new NativeApplicationRestService();
    private final NativeAccountRestService accountService = new NativeAccountRestService();
    private final NativeWorkgroupRestService workgroupService = new NativeWorkgroupRestService();
    private final NativeRoleRestService roleService = new NativeRoleRestService();
    private final NativeIdentityRoleRestService identityRoleService = new NativeIdentityRoleRestService();
    private final NativeIdentityEntitlementRestService identityEntitlementService = new NativeIdentityEntitlementRestService();
    private final NativeAccountEntitlementRestService accountEntitlementService = new NativeAccountEntitlementRestService();
    private final NativeRoleEntitlementRestService roleEntitlementService = new NativeRoleEntitlementRestService();
    private final NativeIdentityRequestRestService identityRequestService = new NativeIdentityRequestRestService();
    private final NativeIdentityRequestItemRestService identityRequestItemService = new NativeIdentityRequestItemRestService();
    private final NativeIdentityRequestApprovalRestService identityRequestApprovalService = new NativeIdentityRequestApprovalRestService();
    private final NativePolicyRestService policyService = new NativePolicyRestService();
    private final NativeProvisioningItemRestService provisioningItemService = new NativeProvisioningItemRestService();

    private HttpServer server;

    public KfAgentRestServer(AppConfig iiqConfig, String host, int port) {
        this.host = host;
        this.port = port;
        this.session = new IiqSessionClient(iiqConfig); // authenticates lazily, session reused across requests
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext(PREFIX, this::route);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("KF Agent REST listening on http://" + host + ":" + port + PREFIX
                + "/{entitlements,identities,applications,accounts,workgroups,roles,identity-roles,identity-entitlements,account-entitlements,role-entitlements,identity-requests,identity-request-items,identity-request-approvals,policies,provisioning-items}  (read-only; no PostgreSQL on this path)");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, error("method not allowed; use GET"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            String sub = path.length() > PREFIX.length() ? path.substring(PREFIX.length()) : "";
            while (sub.startsWith("/")) {
                sub = sub.substring(1);
            }
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());

            if ("entitlements".equals(sub)) {
                handleEntitlements(ex, q);
            } else if ("identities".equals(sub)) {
                handleIdentities(ex, q);
            } else if ("applications".equals(sub)) {
                handleApplications(ex, q);
            } else if ("accounts".equals(sub)) {
                handleAccounts(ex, q);
            } else if ("workgroups".equals(sub)) {
                handleWorkgroups(ex, q);
            } else if ("roles".equals(sub)) {
                handleRoles(ex, q);
            } else if ("identity-roles".equals(sub)) {
                handleIdentityRoles(ex, q);
            } else if ("identity-entitlements".equals(sub)) {
                handleIdentityEntitlements(ex, q);
            } else if ("account-entitlements".equals(sub)) {
                handleAccountEntitlements(ex, q);
            } else if ("role-entitlements".equals(sub)) {
                handleRoleEntitlements(ex, q);
            } else if ("identity-requests".equals(sub)) {
                handleIdentityRequests(ex, q);
            } else if ("identity-request-items".equals(sub)) {
                handleIdentityRequestItems(ex, q);
            } else if ("identity-request-approvals".equals(sub)) {
                handleIdentityRequestApprovals(ex, q);
            } else if ("policies".equals(sub)) {
                handlePolicies(ex, q);
            } else if ("provisioning-items".equals(sub)) {
                handleProvisioningItems(ex, q);
            } else {
                writeJson(ex, 404, error("unknown resource '" + sub
                        + "' — implemented: 'entitlements', 'identities', 'applications', 'accounts', 'workgroups', 'roles', 'identity-roles', 'identity-entitlements', 'account-entitlements', 'role-entitlements', 'identity-requests', 'identity-request-items', 'identity-request-approvals', 'policies', 'provisioning-items'"));
            }
        } catch (Exception e) {
            writeJson(ex, 500, error(e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage())));
        }
    }

    /** Reserved query params (paging + the one native server-side bound); everything else is a field filter. */
    private static final java.util.Set<String> RESERVED =
            java.util.Set.of("start", "limit", "modifiedAfter");

    private void handleEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        // Generic filtering: every non-reserved query param is an exact filter on the response field of
        // the same name (our DB/response field names, e.g. source_id, value, type, is_group_type).
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // modifiedAfter is the ONE native server-side bound; applied by building the page source with it.
        NativeManagedAttributeClient client = new NativeManagedAttributeClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeManagedAttributePageSource source = client;

        try {
            List<Map<String, Object>> rows = entitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentities(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeIdentityClient client = new NativeIdentityClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeIdentityPageSource source = client;

        try {
            List<Map<String, Object>> rows = identityService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleApplications(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeApplicationClient client = new NativeApplicationClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeApplicationPageSource source = client;

        try {
            List<Map<String, Object>> rows = applicationService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAccounts(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeLinkClient client = new NativeLinkClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeLinkPageSource source = client;

        try {
            List<Map<String, Object>> rows = accountService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleWorkgroups(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native Workgroup client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeWorkgroupPageSource source = new NativeWorkgroupClient(session);

        try {
            List<Map<String, Object>> rows = workgroupService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleRoles(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        NativeRoleClient client = new NativeRoleClient(session);
        String modifiedAfter = q.get("modifiedAfter");
        if (modifiedAfter != null && !modifiedAfter.trim().isEmpty()) {
            client = client.withModifiedAfter(modifiedAfter);
        }
        NativeRolePageSource source = client;

        try {
            List<Map<String, Object>> rows = roleService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityRoles(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native identity-role client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeIdentityRolePageSource source = new NativeIdentityRoleClient(session);

        try {
            List<Map<String, Object>> rows = identityRoleService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native identity-entitlement client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeIdentityEntitlementPageSource source = new NativeIdentityEntitlementClient(session);

        try {
            List<Map<String, Object>> rows = identityEntitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleAccountEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native account-entitlement client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeAccountEntitlementPageSource source = new NativeAccountEntitlementClient(session);

        try {
            List<Map<String, Object>> rows = accountEntitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleRoleEntitlements(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native Bundle-relationship client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeRoleRelationshipPageSource source = new NativeRoleRelationshipClient(session);

        try {
            List<Map<String, Object>> rows = roleEntitlementService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityRequests(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native identity-request client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session);

        try {
            List<Map<String, Object>> rows = identityRequestService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityRequestItems(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Items are extracted by the SHARED IdentityRequest native import; the item service collects only
        // the item rows. No modifiedAfter support (full-scan only); modifiedAfter stays reserved/ignored.
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session);

        try {
            List<Map<String, Object>> rows = identityRequestItemService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleIdentityRequestApprovals(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Approvals are extracted by the SHARED IdentityRequest native import; the approval service collects
        // only the approval rows. No modifiedAfter support (full-scan only); modifiedAfter stays reserved.
        NativeIdentityRequestPageSource source = new NativeIdentityRequestClient(session);

        try {
            List<Map<String, Object>> rows = identityRequestApprovalService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handlePolicies(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // The native Policy client has no server-side modifiedAfter support (full-scan only);
        // modifiedAfter stays reserved (never a field filter) but is simply not applied here.
        NativePolicyPageSource source = new NativePolicyClient(session);

        try {
            List<Map<String, Object>> rows = policyService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleProvisioningItems(HttpExchange ex, Map<String, String> q) throws IOException {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!RESERVED.contains(e.getKey())) {
                filters.put(e.getKey(), e.getValue());
            }
        }
        Integer start = intOrNull(q.get("start"));
        Integer limit = intOrNull(q.get("limit"));

        // Items are derived by the SHARED ProvisioningTransaction native import; the item service collects
        // only the item rows. No modifiedAfter support (full-scan only); modifiedAfter stays reserved.
        NativeProvisioningTxnPageSource source = new NativeProvisioningTxnClient(session);

        try {
            List<Map<String, Object>> rows = provisioningItemService.fetch(source, filters, start, limit);
            writeJson(ex, 200, mapper.writeValueAsBytes(rows)); // plain JSON array, no wrapper
        } catch (IllegalArgumentException bad) {
            writeJson(ex, 400, error(bad.getMessage())); // unknown filter field
        }
    }

    private void handleHealth(HttpExchange ex) throws IOException {
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        ok.put("service", "kfagent");
        writeJson(ex, 200, mapper.writeValueAsBytes(ok));
    }

    // --- helpers ------------------------------------------------------------

    private static Integer intOrNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return out;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.put(decode(pair), "");
            } else {
                out.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return out;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private byte[] error(String message) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("error", message);
        try {
            return mapper.writeValueAsBytes(e);
        } catch (Exception ex) {
            return ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    private void writeJson(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
