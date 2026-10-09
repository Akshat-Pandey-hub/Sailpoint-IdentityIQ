package com.keyforge.iiq;

import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.parquet.ParquetConfig;
import com.keyforge.iiq.rest.KfAgentRestServer;
import com.keyforge.iiq.rest.ParquetRestServer;
import com.keyforge.iiq.rest.RestConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Covers the combined {@code start-kfagent} startup wiring without a live IdentityIQ server or real
 * credentials: KF Agent and the Parquet query service are mounted as separate path contexts on ONE shared
 * {@link HttpServer} (a single port), proving their routes coexist ({@code /health}, {@code /kfagent/*},
 * {@code /iiq_parquet/*}, {@code /native_parquet/*}), that Parquet mounts with NO datasets present, and that
 * a single {@code stop()} tears everything down. Uses an ephemeral port — no fixed production ports, no
 * external infrastructure.
 */
class StartKfAgentCombinedTest {

    /** A free localhost port (found, then released for immediate reuse — standard test practice). */
    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Some locked-down/CI sandboxes forbid loopback sockets (the NIO {@code Selector} pipe used by both the
     * JDK HTTP server and client). KF Agent's constructor builds a {@link HttpClient}, and our probe + the
     * server itself need loopback. When unavailable, skip the networked test rather than fail — it runs
     * normally on a developer machine or CI with loopback enabled.
     */
    private static void assumeLoopbackAvailable() {
        try {
            HttpClient.newHttpClient();
        } catch (Throwable t) {
            assumeTrue(false, "loopback networking unavailable in this environment: " + t);
        }
    }

    private static int httpStatus(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(2000);
        c.setReadTimeout(2000);
        c.setRequestMethod("GET");
        try {
            return c.getResponseCode();
        } finally {
            c.disconnect();
        }
    }

    @Test
    void bothServicesCoexistOnOneSharedPortAndShutDownTogether(@TempDir Path emptyParquetDir) throws Exception {
        assumeLoopbackAvailable();
        int port = freePort();
        String base = "http://127.0.0.1:" + port;

        // One HttpServer, one port: mount KF Agent (/health, /kfagent/*) + Parquet (/iiq_parquet/*,
        // /native_parquet/*) — exactly what runStartKfAgent() does.
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        http.setExecutor(Executors.newFixedThreadPool(4));

        // Dummy IIQ config: the KF Agent constructor only stores it (lazy session); /health never calls IIQ.
        KfAgentRestServer kfAgent =
                new KfAgentRestServer(new AppConfig("http://localhost:1/identityiq", "u", "p"), "127.0.0.1", port);
        kfAgent.registerInto(http); // owns /health

        // Empty temp dir -> proves the Parquet endpoints mount with NO datasets present. includeHealth=false
        // because KF Agent already owns /health (two contexts on one path would be illegal).
        ParquetRestServer parquet =
                new ParquetRestServer(new RestConfig("127.0.0.1", 0), new ParquetConfig(emptyParquetDir));
        parquet.registerInto(http, false);

        http.start();
        try {
            // All four route families answer on the SAME port.
            assertEquals(200, httpStatus(base + "/health"), "shared /health");
            assertEquals(200, httpStatus(base + "/iiq_parquet/datasets"), "Parquet REST datasets (no files needed)");
            assertEquals(200, httpStatus(base + "/native_parquet/datasets"), "Parquet native datasets");
            // KF Agent context is mounted: a data request is routed (not a 404 'no context'). It reaches the
            // handler, which then fails to contact the dummy IIQ -> a 4xx/5xx, never 404.
            assertNotEquals(404, httpStatus(base + "/kfagent/identities?limit=1"), "KF Agent route is mounted");
        } finally {
            http.stop(0);
        }

        // Clean shutdown: the shared port is no longer served (a fresh request fails to connect).
        assertThrows(IOException.class, () -> httpStatus(base + "/health"),
                "server stopped -> connection refused");
    }
}
