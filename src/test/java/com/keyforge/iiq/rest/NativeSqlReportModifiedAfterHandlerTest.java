package com.keyforge.iiq.rest;

import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.iiq.config.AppConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeSqlReportModifiedAfterHandlerTest {
    private static final String BOUND = "2026-10-12T10:00:00Z";
    private static final String AUTH = "Basic " + java.util.Base64.getEncoder().encodeToString(
            "tester:test-only-password".getBytes(StandardCharsets.UTF_8));

    private static final class RecordingSession extends IiqSessionClient {
        final List<Map<String, String>> requests = new ArrayList<>();
        RecordingSession() { super("http://localhost:1/identityiq", "u", "p", null); }
        @Override public void warmCsrfToken() { }
        @Override public String get(String path, Map<String, String> params) {
            requests.add(new LinkedHashMap<>(params));
            return "{\"columns\":[],\"rows\":[],\"truncated\":false,\"hasMore\":false,\"pagingApplied\":true}";
        }
    }

    @Test
    void allFourReportHandlersForwardModifiedAfterToTheClient() throws Exception {
        HttpServer http = null;
        try {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RecordingSession session = new RecordingSession();
            RestAuthFilter auth = RestAuthFilter.of(RestCredentials.of("tester", "test-only-password"));
            new KfAgentRestServer(new AppConfig("http://localhost:1/identityiq", "u", "p"),
                    "127.0.0.1", 0, auth, session).registerInto(http);
            http.start();
            String base = "http://127.0.0.1:" + http.getAddress().getPort() + "/kfagent/";
            String[] routes = {"entitlement-assignment", "workgroup-members", "entitlement-certification",
                    "entitlement-certification-status"};
            for (String route : routes) {
                HttpURLConnection conn = (HttpURLConnection) new URL(base + route + "?modifiedAfter=" + BOUND).openConnection();
                conn.setRequestProperty("Authorization", AUTH);
                try {
                    assertEquals(200, conn.getResponseCode(), route);
                } finally {
                    conn.disconnect();
                }
            }
            assertEquals(4, session.requests.size());
            for (Map<String, String> request : session.requests) {
                assertEquals(BOUND, request.get("modifiedAfter"));
                assertTrue(!request.containsKey("start") && !request.containsKey("limit"));
            }
        } catch (java.io.IOException blocked) {
            assumeTrue(false, "loopback networking unavailable in this environment: " + blocked);
        } finally {
            if (http != null) http.stop(0);
        }
    }
}
