package com.keyforge.iiq.rest;

import com.keyforge.iiq.config.AppConfig;
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
 *       configured IdentityIQ username/password ({@code IIQ_USERNAME} / {@code IIQ_PASSWORD}). Missing or wrong →
 *       {@code 401 {"error":"Unauthorized"}} with {@code WWW-Authenticate: Basic}; the handler never runs, so no data
 *       is exposed. If no credentials are configured, every request is rejected (fail closed).</li>
 *   <li><b>Per-request safety net</b>: anything a handler throws (including an {@link Error}) is logged server-side
 *       and answered with a generic {@code 500 {"error":"Internal Server Error"}}; the server keeps serving.</li>
 * </ol>
 * Never logs passwords or the Authorization header.
 */
public final class RestAuthFilter extends Filter {

    static final String REALM = "KF Agent REST";
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

    /** Guard that accepts the configured IdentityIQ login ({@code IIQ_USERNAME} / {@code IIQ_PASSWORD}). */
    public static RestAuthFilter forIiqLogin(AppConfig iiqConfig) {
        return new RestAuthFilter(RestCredentials.of(iiqConfig.getUsername(), iiqConfig.getPassword()));
    }

    /**
     * Same, loading the IdentityIQ login from the environment; if it is not configured the guard rejects every
     * request (the service still starts) — used by start-rest, which does not otherwise need IIQ.
     */
    public static RestAuthFilter fromEnvironment() {
        try {
            return forIiqLogin(AppConfig.load());
        } catch (RuntimeException notConfigured) {
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
                ? "REST API authentication: HTTP Basic (IIQ_USERNAME / IIQ_PASSWORD, user '" + credentials.username() + "')"
                : "[WARN] REST API authentication: IIQ_USERNAME / IIQ_PASSWORD are not set — every request will be "
                + "rejected with 401";
    }

    @Override
    public String description() {
        return "HTTP Basic authentication + per-request error guard";
    }

    @Override
    public void doFilter(HttpExchange ex, Chain chain) throws IOException {
        try {
            if (!authorized(ex.getRequestHeaders().getFirst("Authorization"))) {
                System.err.println("[auth] 401 " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                        + " from " + (ex.getRemoteAddress() == null ? "?" : ex.getRemoteAddress().getAddress()));
                ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
                send(ex, 401, UNAUTHORIZED);
                return;
            }
            chain.doFilter(ex);
        } catch (Throwable t) {
            System.err.println("[rest] " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                    + " failed: " + t);
            t.printStackTrace();
            if (ex.getResponseCode() == -1) { // nothing sent yet: answer this caller, keep the server serving
                try {
                    send(ex, 500, INTERNAL);
                } catch (Throwable ignored) {
                    // connection already gone; nothing more to do for this request
                }
            }
        } finally {
            ex.close();
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
