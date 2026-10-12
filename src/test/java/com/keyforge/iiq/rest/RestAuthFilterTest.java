package com.keyforge.iiq.rest;

import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.parquet.ParquetConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * REST API Basic auth ({@link RestAuthFilter}, {@link RestCredentials}): every context of the combined server
 * (/health, /kfagent/*, /iiq_parquet/*, /native_parquet/*) rejects missing/wrong credentials with a JSON 401 and
 * {@code WWW-Authenticate: Basic}; the accepted credential is the configured IIQ login; an unconfigured guard rejects
 * everything; handler failures become a generic JSON 500 and never stop the server. No live IIQ (dummy config).
 */
class RestAuthFilterTest {

    private static final String USER = "tester";
    private static final String PASS = "correct horse battery";

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    // --- credential store --------------------------------------------------------

    @Test
    void credentialsMatchOnlyTheExactUserAndPassword() {
        RestCredentials c = RestCredentials.of(USER, PASS);
        assertTrue(c.matches(USER, PASS));
        assertFalse(c.matches(USER, "wrong"));
        assertFalse(c.matches("Tester", PASS), "username is case-sensitive");
        assertFalse(c.matches(null, PASS));
        assertThrows(IllegalArgumentException.class, () -> RestCredentials.of(USER, ""));
        assertThrows(IllegalArgumentException.class, () -> RestCredentials.of(" ", PASS));
    }

    @Test
    void guardUsesTheConfiguredIiqLogin() {
        RestAuthFilter f = RestAuthFilter.forIiqLogin(new AppConfig("http://localhost:1/identityiq", "spadmin", "s3cret!"));
        assertTrue(f.authorized(basic("spadmin", "s3cret!")));
        assertFalse(f.authorized(basic("spadmin", "other")));
        assertTrue(f.describe().contains("'spadmin'"));
        assertFalse(f.describe().contains("s3cret!"), "the password is never printed");
    }

    @Test
    void anUnconfiguredGuardRejectsEverything() {
        RestAuthFilter none = RestAuthFilter.unconfigured();
        assertFalse(none.isConfigured());
        assertFalse(none.authorized(basic(USER, PASS)));
        assertTrue(none.describe().contains("rejected with 401"));
    }

    @Test
    void headerParsing() {
        RestAuthFilter f = RestAuthFilter.of(RestCredentials.of(USER, PASS));
        assertTrue(f.authorized(basic(USER, PASS)));
        assertTrue(f.authorized("basic " + basic(USER, PASS).substring(6)), "scheme is case-insensitive");
                assertFalse(f.authorized(null));
        assertFalse(f.authorized(basic(USER, "nope")));
        assertFalse(f.authorized("Bearer abc"));
        assertFalse(f.authorized("Basic !!!not-base64"));
        assertFalse(f.authorized("Basic " + Base64.getEncoder().encodeToString("nocolon".getBytes())));
    }

    // --- over HTTP ---------------------------------------------------------------

    private record Reply(int status, String body, String wwwAuthenticate) { }

    private static Reply get(String url, String authorization) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(2000);
        c.setReadTimeout(5000);
        if (authorization != null) {
            c.setRequestProperty("Authorization", authorization);
        }
        try {
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Reply(status, body, c.getHeaderField("WWW-Authenticate"));
        } finally {
            c.disconnect();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void assumeLoopbackAvailable() {
        try {
            HttpClient.newHttpClient();
        } catch (Throwable t) {
            assumeTrue(false, "loopback networking unavailable in this environment: " + t);
        }
    }

    /** The start-kfagent layout: KF Agent + Parquet on one port, one shared guard. */
    private static HttpServer combined(int port, RestAuthFilter auth, Path parquetDir) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        http.setExecutor(Executors.newFixedThreadPool(4));
        new KfAgentRestServer(new AppConfig("http://localhost:1/identityiq", "u", "p"), "127.0.0.1", port, auth)
                .registerInto(http);
        new ParquetRestServer(new RestConfig("127.0.0.1", 0), new ParquetConfig(parquetDir), auth)
                .registerInto(http, false);
        http.start();
        return http;
    }

    @Test
    void everyEndpointRequiresValidBasicAuth(@org.junit.jupiter.api.io.TempDir Path parquetDir) throws Exception {
        assumeLoopbackAvailable();
        int port = freePort();
        String base = "http://127.0.0.1:" + port;
        HttpServer http = combined(port, RestAuthFilter.of(RestCredentials.of(USER, PASS)),
                parquetDir);
        try {
            String[] paths = {"/health", "/kfagent/identities", "/kfagent/no-such-thing", "/iiq_parquet/datasets",
                    "/native_parquet/datasets"};
            for (String p : paths) {
                for (String bad : new String[]{null, basic(USER, "wrong"), basic("intruder", PASS), "Bearer x"}) {
                    Reply r = get(base + p, bad);
                    assertEquals(401, r.status(), p + " with " + bad);
                    assertEquals("{\"error\":\"Unauthorized\"}", r.body(), p);
                    assertTrue(r.wwwAuthenticate() != null && r.wwwAuthenticate().startsWith("Basic"), p);
                }
            }
            assertEquals(200, get(base + "/health", basic(USER, PASS)).status());
            assertEquals(200, get(base + "/iiq_parquet/datasets", basic(USER, PASS)).status());
            assertEquals(200, get(base + "/native_parquet/datasets", basic(USER, PASS)).status());
            assertEquals(404, get(base + "/kfagent/no-such-thing", basic(USER, PASS)).status());
            assertEquals(400, get(base + "/kfagent/identities?limit=abc", basic(USER, PASS)).status());
        } finally {
            http.stop(0);
        }
    }

    @Test
    void handlerFailuresBecomeGenericJson500AndTheServerKeepsServing() throws Exception {
        assumeLoopbackAvailable();
        int port = freePort();
        String base = "http://127.0.0.1:" + port;
        RestAuthFilter auth = RestAuthFilter.of(RestCredentials.of(USER, PASS));
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        http.setExecutor(Executors.newFixedThreadPool(2));
        auth.protect(http.createContext("/boom", ex -> {
            throw new IllegalStateException("secret internal detail C:\\internal\\path");
        }));
        auth.protect(http.createContext("/fatal", ex -> {
            throw new StackOverflowError("secret fatal detail");
        }));
        new KfAgentRestServer(new AppConfig("http://localhost:1/identityiq", "u", "p"), "127.0.0.1", port, auth)
                .registerInto(http);
        http.start();
        try {
            for (String p : new String[]{"/boom", "/fatal"}) {
                Reply r = get(base + p, basic(USER, PASS));
                assertEquals(500, r.status(), p);
                assertEquals("{\"error\":\"Internal Server Error\"}", r.body(), "no internal details leak");
                assertEquals(200, get(base + "/health", basic(USER, PASS)).status(), "still serving after " + p);
            }
            // a KF Agent request that fails inside (dummy, unreachable IIQ) also leaks nothing
            Reply kf = get(base + "/kfagent/identities?limit=1", basic(USER, PASS));
            assertTrue(kf.status() >= 400, String.valueOf(kf.status()));
            assertFalse(kf.body().contains("localhost") || kf.body().contains("Exception"), kf.body());
            assertEquals(200, get(base + "/health", basic(USER, PASS)).status());
        } finally {
            http.stop(0);
        }
    }
}
