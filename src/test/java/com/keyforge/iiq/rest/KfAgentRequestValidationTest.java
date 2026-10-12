package com.keyforge.iiq.rest;

import com.keyforge.iiq.config.AppConfig;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bad client requests get a JSON error and the KF Agent keeps serving: invalid {@code start}/{@code limit}/
 * {@code modifiedAfter} are rejected with 400 before any IIQ call (previously silently ignored — a bad limit
 * returned everything, a bad modifiedAfter collapsed to a full scan), and malformed query encoding is a 400,
 * not a 500. Uses a dummy, unreachable IIQ config: every asserted response is produced without contacting IIQ.
 */
class KfAgentRequestValidationTest {

    private static Map<String, String> q(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void validOrAbsentReservedParamsPass() {
        assertNull(KfAgentRestServer.invalidReservedParam(q()));
        assertNull(KfAgentRestServer.invalidReservedParam(q("start", "0", "limit", "500")));
        assertNull(KfAgentRestServer.invalidReservedParam(q("start", " 10 ", "limit", "-1")), "range is the service's job");
        assertNull(KfAgentRestServer.invalidReservedParam(q("limit", "", "modifiedAfter", " ")), "blank = absent");
        assertNull(KfAgentRestServer.invalidReservedParam(q("modifiedAfter", "2026-10-12T10:00:00Z")),
                "Part 2's Instant.toString() format");
        assertNull(KfAgentRestServer.invalidReservedParam(q("modifiedAfter", "2026-10-12T10:00:00.123456Z")));
        assertNull(KfAgentRestServer.invalidReservedParam(q("modifiedAfter", "2026-10-12T15:30:00+05:30")));
        assertNull(KfAgentRestServer.invalidReservedParam(q("name", "anything", "type", "x")), "filters untouched");
    }

    @Test
    void invalidReservedParamsAreRejected() {
        assertTrue(KfAgentRestServer.invalidReservedParam(q("limit", "abc")).contains("'limit'"));
        assertTrue(KfAgentRestServer.invalidReservedParam(q("start", "1.5")).contains("'start'"));
        assertTrue(KfAgentRestServer.invalidReservedParam(q("limit", "99999999999")).contains("'limit'"));
        assertTrue(KfAgentRestServer.invalidReservedParam(q("modifiedAfter", "yesterday")).contains("modifiedAfter"));
        assertTrue(KfAgentRestServer.invalidReservedParam(q("modifiedAfter", "2026-10-12")).contains("modifiedAfter"),
                "a date without time/zone is not an instant (the plugin would have ignored it)");
        assertTrue(KfAgentRestServer.invalidReservedParam(q("modifiedAfter", "2026-10-12T15:30:00 05:30"))
                .contains("URL-encoded"), "an unencoded '+' decodes to a space");
    }

    // --- over real HTTP: bad requests get JSON errors and the server keeps serving --------------------------

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

    private record Reply(int status, String body) { }

    private static Reply call(String method, String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(2000);
        c.setReadTimeout(5000);
        c.setRequestMethod(method);
        try {
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Reply(status, body);
        } finally {
            c.disconnect();
        }
    }

    @Test
    void badRequestsGetJsonErrorsAndTheServerKeepsServing() throws Exception {
        assumeLoopbackAvailable();
        int port = freePort();
        String base = "http://127.0.0.1:" + port;
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        http.setExecutor(Executors.newFixedThreadPool(4));
        new KfAgentRestServer(new AppConfig("http://localhost:1/identityiq", "u", "p"), "127.0.0.1", port)
                .registerInto(http);
        http.start();
        try {
            Reply badLimit = call("GET", base + "/kfagent/identities?limit=abc");
            assertEquals(400, badLimit.status());
            assertTrue(badLimit.body().contains("\"error\"") && badLimit.body().contains("limit"), badLimit.body());

            Reply badStart = call("GET", base + "/kfagent/entitlement-assignment?start=x");
            assertEquals(400, badStart.status());

            Reply badSince = call("GET", base + "/kfagent/accounts?modifiedAfter=not-a-date");
            assertEquals(400, badSince.status());
            assertTrue(badSince.body().contains("modifiedAfter"), badSince.body());

            // the JDK server itself rejects an invalid %-escape with 400 before routing; our handler's own
            // malformed-query guard is defence in depth — either way a 400, never a 500 or a crash
            assertEquals(400, call("GET", base + "/kfagent/identities?name=%zz").status());

            Reply unknown = call("GET", base + "/kfagent/no-such-thing");
            assertEquals(404, unknown.status());
            assertTrue(unknown.body().contains("unknown resource"), unknown.body());

            assertEquals(405, call("POST", base + "/kfagent/identities").status());

            // still alive and serving after every bad request
            assertEquals(200, call("GET", base + "/health").status());
        } finally {
            http.stop(0);
        }
    }
}
