package com.keyforge.iiq.rest;

import com.keyforge.iiq.client.IiqSessionClient;
import com.keyforge.iiq.config.AppConfig;
import com.keyforge.iiq.nativeparquet.NativeParquetService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeParquetExportRouteTest {
    private static final RestAuthFilter TEST_AUTH = RestAuthFilter.of(RestCredentials.of("tester", "test-only-password"));

    @Test
    void unsetOutputDirReturnsClientErrorWithoutCallingExporter() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        KfAgentRestServer server = server((session, dir, run, dataset) -> { called.set(true); return List.of(); }, Optional::empty);
        FakeExchange ex = new FakeExchange("POST", "/kfagent/parquet/export");
        server.route(ex);
        assertEquals(400, ex.status);
        assertTrue(ex.body().contains("PARQUET_OUT_DIR"));
        assertFalse(called.get());
    }

    @Test
    void postIsAcceptedOnlyForParquetExport() throws Exception {
        KfAgentRestServer server = server((session, dir, run, dataset) -> List.of(
                new NativeParquetService.Result(dataset, 1, 1, 0, 0, dir.resolve("current.parquet").toString(), null)),
                () -> Optional.of(Path.of("unused-output")));
        FakeExchange export = new FakeExchange("POST", "/kfagent/parquet/export/kf_audit_event");
        server.route(export);
        assertEquals(200, export.status);
        assertTrue(export.body().contains("kf_audit_event"));
        FakeExchange getExport = new FakeExchange("GET", "/kfagent/parquet/export");
        server.route(getExport);
        assertEquals(405, getExport.status);
        FakeExchange postOther = new FakeExchange("POST", "/kfagent/identities");
        server.route(postOther);
        assertEquals(405, postOther.status);
    }

    @Test
    void concurrentExportReturnsConflict() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        KfAgentRestServer server = server((session, dir, run, dataset) -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return List.of();
        }, () -> Optional.of(Path.of("unused-output")));
        FakeExchange first = new FakeExchange("POST", "/kfagent/parquet/export");
        Thread worker = new Thread(() -> {
            try { server.route(first); } catch (Exception e) { throw new RuntimeException(e); }
        });
        worker.start();
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        FakeExchange second = new FakeExchange("POST", "/kfagent/parquet/export");
        server.route(second);
        assertEquals(409, second.status);
        release.countDown();
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertEquals(200, first.status);
    }

    private static KfAgentRestServer server(KfAgentRestServer.ParquetExportRunner runner,
                                            java.util.function.Supplier<Optional<Path>> outputDir) {
        AppConfig config = new AppConfig("http://localhost:1/identityiq", "u", "p");
        return new KfAgentRestServer(config, "127.0.0.1", 0, TEST_AUTH, (IiqSessionClient) null, outputDir, runner);
    }

    private static final class FakeExchange extends HttpExchange {
        private final String method;
        private final URI uri;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream response = new ByteArrayOutputStream();
        private int status = -1;

        FakeExchange(String method, String path) { this.method = method; this.uri = URI.create(path); }
        String body() { return response.toString(java.nio.charset.StandardCharsets.UTF_8); }
        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getResponseBody() { return response; }
        @Override public void sendResponseHeaders(int responseCode, long responseLength) { status = responseCode; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 1); }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 0); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream input, OutputStream output) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
