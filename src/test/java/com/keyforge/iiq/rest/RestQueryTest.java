package com.keyforge.iiq.rest;

import com.keyforge.iiq.parquet.DatasetSpec;
import com.keyforge.iiq.parquet.DuckDbParquetWriter;
import com.keyforge.iiq.parquet.Lineage;
import com.keyforge.iiq.parquet.ParquetConfig;
import com.keyforge.iiq.parquet.ParquetDatasets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of the REST query pipeline (QueryParser validation → DuckDB SQL over Parquet),
 * over a local kf_audit_event fixture. No HTTP loopback is used, so these run anywhere. Covers field
 * validation, equality + operator filters, projection, sorting, pagination, invalid input,
 * injection-style rejection, JSON row shape, and latest-run file selection.
 */
class RestQueryTest {

    private static final DatasetCatalog CATALOG = new DatasetCatalog();
    private static final QueryParser PARSER = new QueryParser(CATALOG, 100, 1000);
    private static final ParquetQueryService SERVICE = new ParquetQueryService(CATALOG);
    private static final String DS = "kf_audit_event";

    @TempDir
    static Path outDir;
    static ParquetConfig config;

    @BeforeAll
    static void writeFixture() throws Exception {
        config = new ParquetConfig(outDir);
        DatasetSpec spec = new ParquetDatasets().get(DS).spec();
        List<Map<String, Object>> rows = List.of(
                auditRow("a1", "LOGIN", "Identity:alice", "2026-07-01T00:00:00Z"),
                auditRow("a2", "LOGOUT", "Identity:bob", "2026-07-15T00:00:00Z"),
                auditRow("a3", "UPDATE", "Role:admin", "2026-08-01T00:00:00Z"),
                auditRow("a4", "LOGIN", "Account:svc", "2026-08-15T00:00:00Z"));
        DuckDbParquetWriter.write(spec, rows, config.datasetFile(DS, "run1"));
    }

    private static Map<String, Object> auditRow(String id, String action, String target, String extractedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("audit_event_id", id);
        m.put("source_id", id);
        m.put("action", action);
        m.put("source", "spadmin");
        m.put("target", target);
        m.put("created", "Jul 1, 2026, 12:00 AM");
        return Lineage.stamp(m, new Lineage.Envelope("AuditEvent", id, null, null, null, null, "classic-ui", null),
                "run1", Instant.parse(extractedAt));
    }

    private ParquetQueryService.QueryResult run(Map<String, List<String>> params) {
        QuerySpec spec = PARSER.parse(DS, params);
        return SERVICE.query(spec, config.datasetFile(DS, "run1"));
    }

    private static Map<String, List<String>> p(String... kv) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], List.of(kv[i + 1]));
        }
        return m;
    }

    @Test
    void returnsAllRowsAndFullSchemaByDefault() {
        ParquetQueryService.QueryResult r = run(p());
        assertEquals(4, r.returned());
        assertEquals(4, r.total());
        assertEquals(new ParquetDatasets().get(DS).spec().allColumns().size(), r.columns().size());
    }

    @Test
    void projectsSelectedFieldsOnly() {
        ParquetQueryService.QueryResult r = run(p("fields", "audit_event_id,action"));
        assertEquals(List.of("audit_event_id", "action"), r.columns());
        assertEquals(2, r.rows().get(0).size());
        assertTrue(r.rows().get(0).containsKey("action"));
    }

    @Test
    void simpleEqualityFilter() {
        ParquetQueryService.QueryResult r = run(p("action", "LOGIN"));
        assertEquals(2, r.total());
        assertTrue(r.rows().stream().allMatch(row -> row.get("action").equals("LOGIN")));
    }

    @Test
    void containsOperatorFilter() {
        ParquetQueryService.QueryResult r = run(p("filter.target.contains", "Identity"));
        assertEquals(2, r.total());
    }

    @Test
    void timestampGteOperatorFilter() {
        ParquetQueryService.QueryResult r = run(p("filter.extracted_at.gte", "2026-08-01T00:00:00Z"));
        assertEquals(2, r.total()); // a3, a4
    }

    @Test
    void inOperatorFilter() {
        ParquetQueryService.QueryResult r = run(p("filter.action.in", "LOGIN,UPDATE"));
        assertEquals(3, r.total());
    }

    @Test
    void sortingDescendingWithLimit() {
        ParquetQueryService.QueryResult r = run(p("sort", "action", "order", "desc", "limit", "2"));
        assertEquals(2, r.returned());
        assertEquals("UPDATE", r.rows().get(0).get("action"));
        assertEquals("LOGOUT", r.rows().get(1).get("action"));
    }

    @Test
    void limitAndOffsetPaginate() {
        ParquetQueryService.QueryResult r = run(p("sort", "audit_event_id", "limit", "2", "offset", "2"));
        assertEquals(2, r.returned());
        assertEquals(4, r.total());
        assertEquals("a3", r.rows().get(0).get("audit_event_id"));
    }

    @Test
    void unknownFieldRejected() {
        assertThrows(ApiException.class, () -> run(p("fields", "nope")));
        assertThrows(ApiException.class, () -> run(p("nope", "x")));
        assertThrows(ApiException.class, () -> run(p("sort", "nope")));
    }

    @Test
    void operatorTypeMismatchRejected() {
        // gt on a text column is nonsense -> 400, not invalid SQL
        ApiException e = assertThrows(ApiException.class, () -> run(p("filter.action.gt", "x")));
        assertEquals(400, e.status());
    }

    @Test
    void injectionStyleFieldRejected() {
        // a malicious "column" never reaches SQL: it fails schema validation first
        ApiException e = assertThrows(ApiException.class,
                () -> run(p("fields", "action; DROP TABLE stage")));
        assertEquals(400, e.status());
        assertThrows(ApiException.class, () -> run(p("1=1", "x")));
    }

    @Test
    void limitClampedToMax() {
        QuerySpec spec = PARSER.parse(DS, p("limit", "999999"));
        assertEquals(1000, spec.limit()); // MAX_LIMIT
    }

    @Test
    void latestRunFileIsSelected() throws Exception {
        DatasetSpec spec = new ParquetDatasets().get(DS).spec();
        // second run with only 1 row, written newer
        DuckDbParquetWriter.write(spec, List.of(auditRow("z1", "LOGIN", "Identity:z", "2026-09-01T00:00:00Z")),
                config.datasetFile(DS, "run2"));
        java.nio.file.Files.setLastModifiedTime(config.datasetFile(DS, "run1"),
                java.nio.file.attribute.FileTime.fromMillis(1_000_000L));
        java.nio.file.Files.setLastModifiedTime(config.datasetFile(DS, "run2"),
                java.nio.file.attribute.FileTime.fromMillis(2_000_000L));
        Path latest = new ParquetFileResolver(config).resolve(DS, null).orElseThrow();
        assertTrue(latest.getFileName().toString().contains("run2"));
        // and a specific run can still be selected
        Path specific = new ParquetFileResolver(config).resolve(DS, "run1").orElseThrow();
        assertTrue(specific.getFileName().toString().contains("run1"));
    }
}
