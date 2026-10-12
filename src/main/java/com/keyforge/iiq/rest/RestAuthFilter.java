package com.keyforge.iiq.rest;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single, shared request guard for every REST context ({@code /health}, {@code /kfagent/*},
 * {@code /iiq_parquet/*}, {@code /native_parquet/*}) in every startup mode. Two jobs:
 * <ol>
 *   <li><b>HTTP Basic authentication</b> against the server-side {@link RestCredentials} file. Missing or wrong
 *       credentials → {@code 401 {"error":"Unauthorized"}} with {@code WWW-Authenticate: Basic}; the handler is never
 *       invoked, so no data is exposed. If no credential is configured the service still starts but rejects every
 *       request (fail closed). The file is re-read when it changes, so credentials can be set without a restart.</li>
 *   <li><b>Per-request safety net</b>: anything a handler throws (including an {@link Error}) is logged server-side
 *       and answered with a generic {@code 500 {"error":"Internal Server Error"}}, so one failed request never
 *       stops the service.</li>
 * </ol>
 * Never logs passwords or the Authorization header.
 */
public final class RestAuthFilter extends Filter {

    static final String REALM = "KF Agent REST";
    private static final byte[] UNAUTHORIZED = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INTERNAL = "{\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_CACHED = 32;

    private final Path file;
    private final Object lock = new Object();
    private volatile RestCredentials credentials;
    private volatile FileTime loadedVersion;
    private volatile boolean loadedOnce;
    /** SHA-256 digests of Authorization headers already verified (avoids re-running PBKDF2 per request). */
    private final Set<String> verified = ConcurrentHashMap.newKeySet();

    private RestAuthFilter(Path file, RestCredentials fixed) {
        this.file = file;
        this.credentials = fixed;
        this.loadedOnce = fixed != null;
    }

    /** Guard backed by the server-side credential file ({@link RestCredentials#defaultPath()}). */
    public static RestAuthFilter fromDefaultStore() {
        return fromFile(RestCredentials.defaultPath());
    }

    public static RestAuthFilter fromFile(Path file) {
        RestAuthFilter f = new RestAuthFilter(file, null);
        f.refresh();
        return f;
    }

    /** Guard with a fixed, in-memory credential (tests / embedding). */
    public static RestAuthFilter fixed(RestCredentials credentials) {
        return new RestAuthFilter(null, credentials);
    }

    /** Attaches this guard to a context; call for every context a server registers. */
    public HttpContext protect(HttpContext context) {
        context.getFilters().add(this);
        return context;
    }

    /** Whether a credential is currently configured (false → every request is rejected). */
    public boolean isConfigured() {
        refresh();
        return credentials != null;
    }

    /** One-line startup status, safe to print (no secrets). */
    public String describe() {
        if (isConfigured()) {
            return "REST API authentication: HTTP Basic, user '" + credentials.username() + "'"
                    + (file == null ? "" : " (credential file " + file.toAbsolutePath() + ")");
        }
        return "[WARN] REST API credentials are NOT configured" + (file == null ? "" : " (" + file.toAbsolutePath()
                + ")") + ": every request will be rejected with 401. Run 'set-rest-credentials' to configure them.";
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
                        + " from " + remote(ex));
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

    // --- authentication -------------------------------------------------------

    boolean authorized(String header) {
        RestCredentials current = currentCredentials();
        if (current == null || header == null) {
            return false;
        }
        String h = header.trim();
        if (h.length() < 6 || !h.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        String digest = sha256(h);
        if (verified.contains(digest)) {
            return true;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(h.substring(6).trim());
        } catch (IllegalArgumentException badBase64) {
            return false;
        }
        String pair = new String(decoded, StandardCharsets.UTF_8);
        Arrays.fill(decoded, (byte) 0);
        int colon = pair.indexOf(':');
        if (colon < 0) {
            return false;
        }
        char[] password = pair.substring(colon + 1).toCharArray();
        try {
            if (!current.matches(pair.substring(0, colon), password)) {
                return false;
            }
        } finally {
            Arrays.fill(password, '\0');
        }
        if (verified.size() >= MAX_CACHED) {
            verified.clear();
        }
        verified.add(digest);
        return true;
    }

    private RestCredentials currentCredentials() {
        refresh();
        return credentials;
    }

    /** Re-reads the credential file when it appears, changes or disappears (cheap mtime check). */
    private void refresh() {
        if (file == null) {
            return;
        }
        FileTime now;
        try {
            now = Files.isRegularFile(file) ? Files.getLastModifiedTime(file) : null;
        } catch (IOException e) {
            now = null;
        }
        if (loadedOnce && java.util.Objects.equals(now, loadedVersion)) {
            return;
        }
        synchronized (lock) {
            if (loadedOnce && java.util.Objects.equals(now, loadedVersion)) {
                return;
            }
            RestCredentials next = null;
            try {
                next = RestCredentials.load(file);
            } catch (IOException e) {
                System.err.println("[auth] cannot read REST credential file " + file.toAbsolutePath() + ": "
                        + e.getMessage() + " — rejecting all requests until it is fixed");
            }
            verified.clear();
            credentials = next;
            loadedVersion = now;
            loadedOnce = true;
        }
    }

    // --- helpers --------------------------------------------------------------

    private static void send(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static String remote(HttpExchange ex) {
        return ex.getRemoteAddress() == null ? "?" : String.valueOf(ex.getRemoteAddress().getAddress());
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
