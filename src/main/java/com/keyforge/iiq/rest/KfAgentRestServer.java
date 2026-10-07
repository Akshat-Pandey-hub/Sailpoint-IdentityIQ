package com.keyforge.iiq.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.iiq.config.AppConfig;
import com.keyforge.nativeload.NativeEntitlementRestService;
import com.keyforge.nativeload.NativeManagedAttributeClient;
import com.keyforge.nativeload.NativeManagedAttributePageSource;
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
                + "/entitlements  (read-only; no PostgreSQL on this path)");
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
            } else {
                writeJson(ex, 404, error("unknown resource '" + sub
                        + "' — only 'entitlements' is implemented so far"));
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
