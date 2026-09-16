package com.keyforge.iiq.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.parquet.ParquetConfig;
import com.keyforge.iiq.parquet.ParquetType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Read-only HTTP query service over the Parquet datasets, using the JDK's built-in HTTP server (no web
 * framework) + DuckDB. It never calls IdentityIQ or PostgreSQL. Endpoints:
 * <ul>
 *   <li>{@code GET /health}</li>
 *   <li>{@code GET /iiq_parquet/datasets}</li>
 *   <li>{@code GET /iiq_parquet/{dataset}/schema}</li>
 *   <li>{@code GET /iiq_parquet/{dataset}} — filter / fields / sort / order / limit / offset / run</li>
 * </ul>
 */
public final class ParquetRestServer {

    public static final String PREFIX = "/iiq_parquet";

    private final RestConfig restConfig;
    private final ParquetConfig parquetConfig;
    private final DatasetCatalog catalog;
    private final ParquetFileResolver resolver;
    private final ParquetQueryService queryService;
    private final QueryParser parser;
    private final ObjectMapper json = new ObjectMapper();

    private HttpServer server;

    public ParquetRestServer(RestConfig restConfig, ParquetConfig parquetConfig) {
        this.restConfig = restConfig;
        this.parquetConfig = parquetConfig;
        this.catalog = new DatasetCatalog();
        this.resolver = new ParquetFileResolver(parquetConfig);
        this.queryService = new ParquetQueryService(catalog);
        this.parser = new QueryParser(catalog, RestConfig.DEFAULT_LIMIT, RestConfig.MAX_LIMIT);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(restConfig.host(), restConfig.port()), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext(PREFIX, this::handleParquet);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("REST query service listening on http://" + restConfig.host() + ":" + restConfig.port());
        System.out.println("Parquet output dir: " + parquetConfig.outputDir().toAbsolutePath());
        System.out.println("Datasets: " + catalog.datasets());
        System.out.println("Endpoints: GET /health | GET " + PREFIX + "/datasets | GET " + PREFIX
                + "/{dataset} | GET " + PREFIX + "/{dataset}/schema");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** For tests: the bound port (useful when port 0 is requested). */
    public int boundPort() {
        return server.getAddress().getPort();
    }

    // ---- handlers ----------------------------------------------------------

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, new ApiException(405, "Method Not Allowed", "Only GET is supported"));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("parquetDirAccessible", Files.isDirectory(parquetConfig.outputDir()));
        send(ex, 200, body);
    }

    private void handleParquet(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                throw new ApiException(405, "Method Not Allowed", "Only GET is supported");
            }
            String path = ex.getRequestURI().getPath();
            String rest = path.length() > PREFIX.length() ? path.substring(PREFIX.length()) : "";
            while (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            String[] segs = rest.isEmpty() ? new String[0] : rest.split("/");

            if (segs.length == 0) {
                throw ApiException.notFound("Use " + PREFIX + "/datasets or " + PREFIX + "/{dataset}");
            }
            if (segs.length == 1 && segs[0].equals("datasets")) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("datasets", catalog.datasets());
                send(ex, 200, body);
                return;
            }
            String dataset = segs[0];
            if (segs.length == 2 && segs[1].equals("schema")) {
                send(ex, 200, schemaBody(dataset));
                return;
            }
            if (segs.length == 1) {
                send(ex, 200, queryBody(dataset, parseQuery(ex)));
                return;
            }
            throw ApiException.notFound("Unknown path: " + path);
        } catch (ApiException e) {
            sendError(ex, e);
        } catch (Exception e) {
            System.err.println("[rest] unexpected error: " + e);
            sendError(ex, new ApiException(500, "Internal Server Error", "Unexpected server error"));
        }
    }

    private Map<String, Object> schemaBody(String dataset) {
        Map<String, ParquetType> cols = catalog.columns(dataset); // 404 if unknown
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map.Entry<String, ParquetType> e : cols.entrySet()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", e.getKey());
            c.put("type", e.getValue().name());
            columns.add(c);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dataset", dataset);
        body.put("columns", columns);
        return body;
    }

    private Map<String, Object> queryBody(String dataset, Map<String, List<String>> params) {
        QuerySpec spec = parser.parse(dataset, params); // validates dataset/fields/filters/etc.
        Optional<Path> file = resolver.resolve(dataset, spec.run());
        Map<String, Object> body = new LinkedHashMap<>();
        if (file.isEmpty()) {
            // Known dataset, but nothing extracted yet: return an empty, well-formed result.
            body.put("dataset", dataset);
            body.put("columns", spec.fields().isEmpty() ? catalog.columnNames(dataset) : spec.fields());
            body.put("rows", List.of());
            body.put("returned", 0);
            body.put("total", 0);
            body.put("limit", spec.limit());
            body.put("offset", spec.offset());
            body.put("note", "no extracted Parquet data for this dataset yet");
            return body;
        }
        ParquetQueryService.QueryResult r = queryService.query(spec, file.get());
        body.put("dataset", r.dataset());
        body.put("columns", r.columns());
        body.put("rows", r.rows());
        body.put("returned", r.returned());
        body.put("total", r.total());
        body.put("limit", r.limit());
        body.put("offset", r.offset());
        return body;
    }

    // ---- query string + response helpers -----------------------------------

    private static Map<String, List<String>> parseQuery(HttpExchange ex) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            out.computeIfAbsent(dec(k), key -> new ArrayList<>()).add(dec(v));
        }
        return out;
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange ex, ApiException e) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.error());
        body.put("message", e.getMessage());
        send(ex, e.status(), body);
    }
}
