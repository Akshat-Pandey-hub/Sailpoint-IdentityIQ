package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.parquet.DatasetSpec;
import com.keyforge.iiq.parquet.DuckDbParquetWriter;
import com.keyforge.iiq.parquet.ParquetConfig;
import com.keyforge.iiq.rest.ApiException;
import com.keyforge.iiq.rest.DatasetCatalog;
import com.keyforge.iiq.rest.ParquetFileResolver;
import com.keyforge.iiq.rest.ParquetQueryService;
import com.keyforge.iiq.rest.QueryParser;
import com.keyforge.iiq.rest.QuerySpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end native query pipeline (native {@link DatasetCatalog} built from {@link NativeParquetDatasets}
 * → {@link QueryParser} validation → {@link ParquetQueryService} DuckDB SQL) over a {@code current.parquet}
 * fixture, reusing the SAME engine as the REST/SCIM path. The critical test is dataset isolation: a decoy
 * REST/SCIM {@code part-*.parquet} in the same directory is never read by the native resolver.
 */
class NativeParquetRestQueryTest {

    private static DatasetCatalog nativeCatalog() {
        List<DatasetSpec> specs = new ArrayList<>();
        for (NativeParquetDatasets.Def d : NativeParquetDatasets.all()) {
            specs.add(d.spec());
        }
        return DatasetCatalog.forSpecs(specs);
    }

    private static final DatasetCatalog CATALOG = nativeCatalog();
    private static final QueryParser PARSER = new QueryParser(CATALOG, 100, 1000);
    private static final ParquetQueryService SERVICE = new ParquetQueryService(CATALOG);
    private static final String DS = "task_result"; // a name that ALSO exists in the REST/SCIM catalog

    @TempDir
    static Path outDir;
    static ParquetConfig config;
    static NativeParquetFileResolver nativeResolver;

    @BeforeAll
    static void writeFixtures() throws Exception {
        config = new ParquetConfig(outDir);
        nativeResolver = new NativeParquetFileResolver(config);
        DatasetSpec spec = def(DS).spec();

        // Native current-state file (what the native endpoint MUST serve).
        List<Map<String, Object>> current = List.of(
                taskRow("t1", "Aggregate A", "Success"),
                taskRow("t2", "Aggregate B", "Error"),
                taskRow("t3", "Aggregate C", "Success"));
        DuckDbParquetWriter.write(spec, current, outDir.resolve(DS).resolve("current.parquet"));

        // Decoy REST/SCIM-style snapshot part file in the SAME dataset directory. The native resolver
        // must ignore this entirely; a leak would surface the sentinel "DECOY" completion_status.
        DuckDbParquetWriter.write(spec, List.of(taskRow("decoy-1", "REST snapshot", "DECOY")),
                config.datasetFile(DS, "restrun"));
    }

    private static NativeParquetDatasets.Def def(String name) {
        return NativeParquetDatasets.all().stream()
                .filter(d -> d.parquetName().equals(name)).findFirst().orElseThrow();
    }

    private static Map<String, Object> taskRow(String id, String name, String completionStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", id);
        m.put("name", name);
        m.put("type", "Aggregation");
        m.put("completion_status", completionStatus);
        m.put("definition_name", "Account Aggregation");
        m.put("launcher", "spadmin");
        m.put("host", "iiq-app-1");
        // lineage dedup key mirrors the real pipeline (not required by the query, included for realism)
        m.put("src_natural_key", id);
        return m;
    }

    /** Runs a query the way the native endpoint does: native resolver → shared engine. */
    private ParquetQueryService.QueryResult run(Map<String, List<String>> params) {
        QuerySpec spec = PARSER.parse(DS, params);
        Path file = nativeResolver.resolve(DS, spec.run()).orElseThrow();
        return SERVICE.query(spec, file);
    }

    private static Map<String, List<String>> p(String... kv) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], List.of(kv[i + 1]));
        }
        return m;
    }

    // ---- isolation (the critical guarantee) --------------------------------

    @Test
    void nativeResolverServesCurrentParquetNotThePartFile() {
        Path resolved = nativeResolver.resolve(DS, null).orElseThrow();
        assertEquals("current.parquet", resolved.getFileName().toString());
        // The REST resolver over the SAME directory would instead pick the part-*.parquet — proving the
        // two families diverge and that a shared dataset name does not cross the streams.
        Path restResolved = new ParquetFileResolver(config).resolve(DS, null).orElseThrow();
        assertTrue(restResolved.getFileName().toString().startsWith("part-"));
    }

    @Test
    void nativeQueryNeverReturnsDecoyRestRows() {
        ParquetQueryService.QueryResult r = run(p());
        assertEquals(3, r.total());
        assertFalse(r.rows().stream().anyMatch(row -> "DECOY".equals(row.get("completion_status"))),
                "native endpoint must never surface REST/SCIM part-file rows");
        assertFalse(r.rows().stream().anyMatch(row -> "decoy-1".equals(row.get("source_id"))));
    }

    // ---- shared engine features, over native data --------------------------

    @Test
    void nativeCatalogExposesExactlyTheTenApprovedDatasets() {
        List<String> ds = CATALOG.datasets();
        assertEquals(10, ds.size());
        assertTrue(ds.containsAll(List.of("kf_cert_item_decision", "kf_access_request", "kf_request_item",
                "kf_request_approval", "kf_provisioning_txn", "kf_provisioning_item", "kf_event_link",
                "kf_violation", "kf_audit_event", "task_result")));
    }

    @Test
    void returnsAllRowsAndFullNativeSchemaByDefault() {
        ParquetQueryService.QueryResult r = run(p());
        assertEquals(3, r.returned());
        assertEquals(def(DS).spec().allColumns().size(), r.columns().size());
    }

    @Test
    void projectsSelectedFieldsOnly() {
        ParquetQueryService.QueryResult r = run(p("fields", "source_id,completion_status"));
        assertEquals(List.of("source_id", "completion_status"), r.columns());
        assertEquals(2, r.rows().get(0).size());
    }

    @Test
    void equalityFilterOverNativeData() {
        ParquetQueryService.QueryResult r = run(p("completion_status", "Success"));
        assertEquals(2, r.total());
        assertTrue(r.rows().stream().allMatch(row -> "Success".equals(row.get("completion_status"))));
    }

    @Test
    void containsOperatorOverNativeData() {
        ParquetQueryService.QueryResult r = run(p("filter.name.contains", "Aggregate"));
        assertEquals(3, r.total());
    }

    @Test
    void sortingDescendingWithLimit() {
        ParquetQueryService.QueryResult r = run(p("sort", "source_id", "order", "desc", "limit", "2"));
        assertEquals(2, r.returned());
        assertEquals("t3", r.rows().get(0).get("source_id"));
        assertEquals("t2", r.rows().get(1).get("source_id"));
    }

    @Test
    void limitAndOffsetPaginate() {
        ParquetQueryService.QueryResult r = run(p("sort", "source_id", "limit", "1", "offset", "1"));
        assertEquals(1, r.returned());
        assertEquals(3, r.total());
        assertEquals("t2", r.rows().get(0).get("source_id"));
    }

    @Test
    void unknownFieldRejectedAgainstNativeSchema() {
        assertThrows(ApiException.class, () -> run(p("fields", "nope")));
        assertThrows(ApiException.class, () -> run(p("nope", "x")));
    }

    @Test
    void unknownNativeDatasetRejected() {
        // a REST-only dataset name that is not in the native catalog must 404 here
        ApiException e = assertThrows(ApiException.class, () -> PARSER.parse("kf_identity", p()));
        assertEquals(404, e.status());
    }
}
