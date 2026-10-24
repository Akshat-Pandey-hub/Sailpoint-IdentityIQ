package com.keyforge.iiq.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.nativeparquet.NativeParquetDatasets;
import com.keyforge.iiq.nativeparquet.NativeParquetFileResolver;
import com.keyforge.iiq.parquet.DatasetSpec;
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
 * framework) + DuckDB. It never calls IdentityIQ or PostgreSQL.
 *
 * <p>Two <b>isolated</b> dataset families are served by the <b>same</b> DuckDB query engine
 * ({@link ParquetQueryService} + {@link QueryParser}), each with its own catalog and file resolver:
 * <ul>
 *   <li>{@code /iiq_parquet/*} — the REST/SCIM-sourced datasets ({@link DatasetCatalog}, latest
 *       {@code part-<run>.parquet} snapshot via {@link ParquetFileResolver}). Unchanged.</li>
 *   <li>{@code /native_parquet/*} — the native-source datasets ({@link NativeParquetDatasets},
 *       {@code current.parquet} via {@link NativeParquetFileResolver}). New, additive.</li>
 * </ul>
 * The two families never read each other's files, even when they share a dataset name.
 *
 * <p>Endpoints (per family, with prefix {@code P}):
 * <ul>
 *   <li>{@code GET /health}</li>
 *   <li>{@code GET P/datasets}</li>
 *   <li>{@code GET P/{dataset}/schema}</li>
 *   <li>{@code GET P/{dataset}} — filter / fields / sort / order / limit / offset / run</li>
 * </ul>
 */
public final class ParquetRestServer {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ParquetRestServer.class);

    public static final String PREFIX = "/iiq_parquet";
    public static final String NATIVE_PREFIX = "/native_parquet";

    private final RestConfig restConfig;
    private final ParquetConfig parquetConfig;
    private final Endpoint rest;
    private final Endpoint nativeEndpoint;
    private final ObjectMapper json = new ObjectMapper();

    private final RestAuthFilter auth;
    private HttpServer server;

    public ParquetRestServer(RestConfig restConfig, ParquetConfig parquetConfig) {
        this(restConfig, parquetConfig, RestAuthFilter.fromEnvironment());
    }

    /** @param auth the REST API Basic-auth guard applied to every context this server registers */
    public ParquetRestServer(RestConfig restConfig, ParquetConfig parquetConfig, RestAuthFilter auth) {
        this.restConfig = restConfig;
        this.parquetConfig = parquetConfig;
        this.auth = auth;

        // REST/SCIM datasets: existing catalog + latest part-file resolver (unchanged behavior).
        DatasetCatalog restCatalog = new DatasetCatalog();
        this.rest = new Endpoint(PREFIX, restCatalog, new ParquetFileResolver(parquetConfig),
                new ParquetQueryService(restCatalog),
                new QueryParser(restCatalog, RestConfig.DEFAULT_LIMIT, RestConfig.MAX_LIMIT));

        // Native datasets: isolated catalog (native specs) + current.parquet resolver. Same engine.
        List<DatasetSpec> nativeSpecs = new ArrayList<>();
        for (NativeParquetDatasets.Def d : NativeParquetDatasets.all()) {
            nativeSpecs.add(d.spec());
        }
        DatasetCatalog nativeCatalog = DatasetCatalog.forSpecs(nativeSpecs);
        this.nativeEndpoint = new Endpoint(NATIVE_PREFIX, nativeCatalog,
                new NativeParquetFileResolver(parquetConfig),
                new ParquetQueryService(nativeCatalog),
                new QueryParser(nativeCatalog, RestConfig.DEFAULT_LIMIT, RestConfig.MAX_LIMIT));
    }

    /**
     * Registers this service's Parquet-query contexts ({@code /iiq_parquet/*}, {@code /native_parquet/*},
     * and optionally {@code /health}) onto a caller-owned {@link HttpServer}. Lets the Parquet query service
     * be co-hosted with KF Agent on a SINGLE port. {@code includeHealth=false} when another co-hosted service
     * already owns {@code /health} (two contexts on the same path are illegal). The caller owns the server
     * lifecycle (create/executor/start/stop).
     */
    public void registerInto(HttpServer target, boolean includeHealth) {
        if (includeHealth) {
            auth.protect(target.createContext("/health", this::handleHealth));
        }
        auth.protect(target.createContext(PREFIX, rest::handle));
        auth.protect(target.createContext(NATIVE_PREFIX, nativeEndpoint::handle));
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(restConfig.host(), restConfig.port()), 0);
        registerInto(server, true);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        LOG.info(auth.describe());
        LOG.info("REST query service listening on http://" + restConfig.host() + ":" + restConfig.port());
        LOG.info("Parquet output dir: " + parquetConfig.outputDir().toAbsolutePath());
        LOG.info("REST/SCIM datasets:  " + rest.catalog.datasets());
        LOG.info("Native datasets:     " + nativeEndpoint.catalog.datasets());
        LOG.info("Endpoints: GET /health"
                + " | GET " + PREFIX + "/datasets | GET " + PREFIX + "/{dataset}[/schema]"
                + " | GET " + NATIVE_PREFIX + "/datasets | GET " + NATIVE_PREFIX + "/{dataset}[/schema]");
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

    // ---- health ------------------------------------------------------------

    private void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Http.sendError(json, ex, new ApiException(405, "Method Not Allowed", "Only GET is supported"));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("parquetDirAccessible", Files.isDirectory(parquetConfig.outputDir()));
        Http.send(json, ex, 200, body);
    }

    // ---- one dataset family (prefix + catalog + resolver + engine) ----------

    /**
     * Handles all requests under one prefix for one isolated dataset family. The dispatch, schema, and
     * query logic are identical across families; only the injected catalog / resolver / query service /
     * parser differ. This is the single place that both {@code /iiq_parquet} and {@code /native_parquet}
     * route through, so their behavior stays in lock-step while their data stays isolated.
     */
    static final class Endpoint {

        private final String prefix;
        private final DatasetCatalog catalog;
        private final FileResolver resolver;
        private final ParquetQueryService queryService;
        private final QueryParser parser;
        private final ObjectMapper json = new ObjectMapper();

        Endpoint(String prefix, DatasetCatalog catalog, FileResolver resolver,
                 ParquetQueryService queryService, QueryParser parser) {
            this.prefix = prefix;
            this.catalog = catalog;
            this.resolver = resolver;
            this.queryService = queryService;
            this.parser = parser;
        }

        void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String rest = path.length() > prefix.length() ? path.substring(prefix.length()) : "";
            while (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            String[] segs = rest.isEmpty() ? new String[0] : rest.split("/");
            logService(path, segs, ex);

            int status = 200;
            Object body;
            try {
                if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                    throw new ApiException(405, "Method Not Allowed", "Only GET is supported");
                }
                if (segs.length == 0) {
                    throw ApiException.notFound("Use " + prefix + "/datasets or " + prefix + "/{dataset}");
                }
                if (segs.length == 1 && segs[0].equals("datasets")) {
                    Map<String, Object> datasets = new LinkedHashMap<>();
                    datasets.put("datasets", catalog.datasets());
                    body = datasets;
                } else if (segs.length == 2 && segs[1].equals("schema")) {
                    body = schemaBody(segs[0]);
                } else if (segs.length == 1) {
                    body = queryBody(segs[0], parseQuery(ex));
                } else {
                    throw ApiException.notFound("Unknown path: " + path);
                }
            } catch (ApiException e) {
                status = e.status();
                body = Http.errorBody(e);
            } catch (Exception e) {
                LOG.error("[rest] unexpected error", e);
                ApiException internal = new ApiException(500, "Internal Server Error", "Unexpected server error");
                status = internal.status();
                body = Http.errorBody(internal);
            }
            Http.send(json, ex, status, body);
            logResult(path, status, body);
        }

        /** SERVICE line, same as the KF Agent routes: what is being read, from where. */
        private void logService(String path, String[] segs, HttpExchange ex) {
            if (!com.keyforge.iiq.debug.DebugLog.enabled()) {
                return;
            }
            String family = NATIVE_PREFIX.equals(prefix)
                    ? "native datasets exported from IdentityIQ" : "REST/SCIM datasets";
            String purpose;
            if (segs.length == 1 && segs[0].equals("datasets")) {
                purpose = "Parquet query: list the available " + family;
            } else if (segs.length == 2 && segs[1].equals("schema")) {
                purpose = "Parquet query: columns of dataset '" + segs[0] + "' (" + family + ")";
            } else if (segs.length == 1) {
                purpose = "Parquet query: rows of dataset '" + segs[0] + "' (" + family + ")";
            } else {
                purpose = "Parquet query: unknown path";
            }
            Map<String, List<String>> q;
            try {
                q = parseQuery(ex);
            } catch (RuntimeException malformed) {
                q = Map.of(); // the request itself reports the bad query; logging never fails it
            }
            com.keyforge.iiq.debug.DebugLog.log("SERVICE", "endpoint", path, "purpose", purpose,
                    "source", "local Parquet files in PARQUET_OUT_DIR (read-only)",
                    "limit", first(q, "limit"), "offset", first(q, "offset"),
                    "PARQUET_OUT_DIR", com.keyforge.iiq.debug.DebugLog.setting("PARQUET_OUT_DIR"));
        }

        /** RESULT line, same as the KF Agent routes; a rejected request (4xx) is a WARN at every level. */
        private static void logResult(String path, int status, Object body) {
            boolean rejected = status >= 400 && status < 500;
            if (!rejected && !com.keyforge.iiq.debug.DebugLog.enabled()) {
                return;
            }
            Object detail = "-";
            if (body instanceof Map<?, ?> m) {
                if (m.containsKey("error")) {
                    detail = "error: " + m.get("message");
                } else if (m.containsKey("rows")) {
                    detail = m.get("returned") + " rows (of " + m.get("total") + ")"
                            + (m.containsKey("note") ? "; " + m.get("note") : "");
                } else if (m.get("datasets") instanceof List<?> l) {
                    detail = l.size() + " datasets";
                } else if (m.get("columns") instanceof List<?> l) {
                    detail = l.size() + " columns";
                }
            }
            if (rejected) {
                com.keyforge.iiq.debug.DebugLog.warn("RESULT", "endpoint", path, "status", status,
                        "returned", detail, "ms", com.keyforge.iiq.debug.DebugLog.elapsedMs());
            } else {
                com.keyforge.iiq.debug.DebugLog.log("RESULT", "endpoint", path, "status", status,
                        "returned", detail, "ms", com.keyforge.iiq.debug.DebugLog.elapsedMs());
            }
        }

        private static String first(Map<String, List<String>> q, String key) {
            List<String> v = q.get(key);
            return v == null || v.isEmpty() ? null : v.get(0);
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

    /** Small JSON HTTP write helpers, shared by the health handler and every dataset family. */
    static final class Http {

        private Http() {
        }

        static void send(ObjectMapper json, HttpExchange ex, int status, Object body) throws IOException {
            byte[] bytes = json.writeValueAsBytes(body);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        static void sendError(ObjectMapper json, HttpExchange ex, ApiException e) throws IOException {
            send(json, ex, e.status(), errorBody(e));
        }

        static Map<String, Object> errorBody(ApiException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.error());
            body.put("message", e.getMessage());
            return body;
        }
    }
}
