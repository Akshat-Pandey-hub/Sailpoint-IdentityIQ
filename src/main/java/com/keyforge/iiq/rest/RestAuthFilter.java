package com.keyforge.iiq.rest;

import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.debug.DebugLog;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The single, shared request guard for every REST context ({@code /health}, {@code /kfagent/*},
 * {@code /iiq_parquet/*}, {@code /native_parquet/*}) in every startup mode. Two jobs:
 * <ol>
 *   <li><b>HTTP Basic authentication</b>: the caller (e.g. Postman → Authorization → Basic Auth) must send the
 *       configured client ID / client secret ({@code KFAGENT_USERNAME} / {@code KFAGENT_PASSWORD}). Missing or wrong →
 *       {@code 401 {"error":"Unauthorized"}} with {@code WWW-Authenticate: Basic}; the handler never runs, so no data
 *       is exposed. If no credentials are configured, every request is rejected (fail closed). The IdentityIQ login
 *       ({@code IIQ_USERNAME} / {@code IIQ_PASSWORD}) is separate: it is used only by Part 1's own IdentityIQ
 *       session and is never accepted here.</li>
 *   <li><b>Per-request safety net</b>: anything a handler throws (including an {@link Error}) is logged server-side
 *       and answered with a generic {@code 500 {"error":"Internal Server Error"}}; the server keeps serving.</li>
 * </ol>
 * Never logs passwords or the Authorization header.
 */
public final class RestAuthFilter extends Filter {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RestAuthFilter.class);

    static final String REALM = "KF Agent REST";

    /** Setting holding the client ID that REST callers send as the Basic-auth username. */
    public static final String KEY_USERNAME = "KFAGENT_USERNAME";
    /** Setting holding the client secret that REST callers send as the Basic-auth password. */
    public static final String KEY_PASSWORD = "KFAGENT_PASSWORD";
    private static final byte[] UNAUTHORIZED = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INTERNAL = "{\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);

    private final RestCredentials credentials; // null = not configured → reject everything

    private RestAuthFilter(RestCredentials credentials) {
        this.credentials = credentials;
    }

    /** Guard that accepts exactly the given credentials. */
    public static RestAuthFilter of(RestCredentials credentials) {
        return new RestAuthFilter(credentials);
    }

    /**
     * Guard that accepts exactly this client ID / client secret. If either is missing or blank the guard is
     * unconfigured and rejects every request (fail closed); it never falls back to the IdentityIQ login.
     */
    public static RestAuthFilter forClientCredentials(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isEmpty()) {
            return unconfigured();
        }
        return of(RestCredentials.of(clientId, clientSecret));
    }

    /**
     * The guard for every startup mode (start-kfagent, start-rest): the client ID / client secret from
     * {@code KFAGENT_USERNAME} / {@code KFAGENT_PASSWORD}, resolved like the IdentityIQ settings (environment,
     * {@code -D} system property or the {@code IIQ_CONFIG_FILE} file). If they are not configured, or the
     * configuration cannot be read, the guard rejects every request (the service still starts).
     */
    public static RestAuthFilter fromEnvironment() {
        try {
            return forClientCredentials(AppConfig.setting(KEY_USERNAME), AppConfig.setting(KEY_PASSWORD));
        } catch (RuntimeException unreadableConfig) {
            return unconfigured();
        }
    }

    /** Guard with no accepted credentials: rejects every request. */
    static RestAuthFilter unconfigured() {
        return new RestAuthFilter(null);
    }

    /** Attaches this guard to a context; called for every context a server registers. */
    public HttpContext protect(HttpContext context) {
        context.getFilters().add(this);
        return context;
    }

    public boolean isConfigured() {
        return credentials != null;
    }

    /** One-line startup status, safe to print (no secrets). */
    public String describe() {
        return isConfigured()
                ? "REST API authentication: HTTP Basic (" + KEY_USERNAME + " / " + KEY_PASSWORD + ", client '"
                + (DebugLog.debugEnabled() ? credentials.username() : "hidden at normal log level") + "')"
                : "[WARN] REST API authentication: " + KEY_USERNAME + " / " + KEY_PASSWORD + " are not set — every "
                + "request will be rejected with 401";
    }

    @Override
    public String description() {
        return "HTTP Basic authentication + per-request error guard";
    }

    @Override
    public void doFilter(HttpExchange ex, Chain chain) throws IOException {
        DebugLog.begin("req");
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String from = ex.getRemoteAddress() == null ? "?" : String.valueOf(ex.getRemoteAddress().getAddress());
        DebugLog.log("REQUEST", "method", ex.getRequestMethod(), "path", ex.getRequestURI().getPath(),
                "query/filters", ex.getRequestURI().getRawQuery() == null ? "-" : ex.getRequestURI().getRawQuery(),
                "from", from, "via", ex.getRequestHeaders().getFirst("X-Forwarded-For") == null
                        ? "direct" : "proxy (X-Forwarded-For " + ex.getRequestHeaders().getFirst("X-Forwarded-For") + ")");
        try {
            String denied = denyReason(header);
            logAuth(header, denied, ex.getRequestMethod() + " " + ex.getRequestURI().getPath(), from);
            if (denied != null) {
                ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
                send(ex, 401, UNAUTHORIZED);
                return;
            }
            chain.doFilter(ex);
        } catch (Throwable t) {
            DebugLog.failure(LOG, ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                    + " failed; returned HTTP 500", t);
            if (ex.getResponseCode() == -1) { // nothing sent yet: answer this caller, keep the server serving
                try {
                    send(ex, 500, INTERNAL);
                } catch (Throwable ignored) {
                    // connection already gone; nothing more to do for this request
                }
            }
        } finally {
            // The outcome is usually logged already (RESULT, a rejection or a failure); then this is a debug detail.
            DebugLog.response("path", ex.getRequestURI().getPath(), "status", ex.getResponseCode(),
                    "ms", DebugLog.elapsedMs());
            DebugLog.end();
            ex.close();
        }
    }

    /** Debug line: what the caller sent, what the service expects, and the decision with its reason. */
    void logAuth(String header, String denied) {
        logAuth(header, denied, null, null);
    }

    /**
     * A rejected request is logged as ONE warning at every level (method, path, caller and the exact reason; the
     * credentials that were sent only at debug). An accepted one is a debug detail.
     */
    void logAuth(String header, String denied, String request, String from) {
        if (denied == null && !DebugLog.debugEnabled()) {
            return;
        }
        String sentUser = null;
        String sentPassword = null;
        String scheme = header == null ? "none (no Authorization header)" : header.trim().split("\\s+")[0];
        String pair = decodeBasic(header);
        if (pair != null) {
            int colon = pair.indexOf(':');
            sentUser = colon < 0 ? pair : pair.substring(0, colon);
            sentPassword = colon < 0 ? null : pair.substring(colon + 1);
        }
        Object[] kv = {"request", request, "from", from, "scheme", scheme, "sentUser", sentUser,
                "sentPassword", sentPassword, "expectedUser", DebugLog.setting(KEY_USERNAME),
                "expectedPassword", DebugLog.setting(KEY_PASSWORD),
                "result", denied == null ? "ALLOW" : "DENY 401", "reason", denied == null ? "credentials match" : denied};
        if (denied != null) {
            DebugLog.warn("AUTH", kv);
        } else {
            DebugLog.log("AUTH", kv);
        }
    }

    /** Why a request is rejected, or null when it is allowed. */
    String denyReason(String header) {
        if (credentials == null) {
            return "NOT_CONFIGURED: KFAGENT_USERNAME and KFAGENT_PASSWORD are not set on the server";
        }
        if (header == null) {
            return "NO_CREDENTIALS: the request has no Authorization header";
        }
        String h = header.trim();
        if (h.length() < 6 || !h.regionMatches(true, 0, "Basic ", 0, 6)) {
            return "NOT_BASIC: the request uses '" + h.split("\\s+")[0] + "' authentication; only Basic is accepted";
        }
        String pair = decodeBasic(header);
        if (pair == null) {
            return "BAD_ENCODING: the Basic Auth value is not valid Base64";
        }
        int colon = pair.indexOf(':');
        if (colon < 0) {
            return "BAD_FORMAT: the Basic Auth value has no ':' between username and password";
        }
        if (credentials.matches(pair.substring(0, colon), pair.substring(colon + 1))) {
            return null;
        }
        return credentials.userMatches(pair.substring(0, colon))
                ? "WRONG_PASSWORD: supplied password does not match the configured KFAGENT_PASSWORD"
                : "WRONG_USERNAME: supplied username does not match the configured KFAGENT_USERNAME";
    }

    private static String decodeBasic(String header) {
        if (header == null) {
            return null;
        }
        String h = header.trim();
        if (h.length() < 6 || !h.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            return new String(Base64.getDecoder().decode(h.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException badBase64) {
            return null;
        }
    }

    boolean authorized(String header) {
        if (credentials == null || header == null) {
            return false;
        }
        String h = header.trim();
        if (h.length() < 6 || !h.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        String pair;
        try {
            pair = new String(Base64.getDecoder().decode(h.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException badBase64) {
            return false;
        }
        int colon = pair.indexOf(':');
        return colon >= 0 && credentials.matches(pair.substring(0, colon), pair.substring(colon + 1));
    }

    private static void send(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
