package com.keyforge.iiq.parquet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes rows to Parquet via {@link DuckDbParquetWriter} and reads them back with DuckDB, exercising
 * all column types, NULL handling, timestamp fidelity, column projection, predicate filtering, and
 * the empty-dataset (schema-only) case.
 */
class DuckDbParquetWriterTest {

    private static final DatasetSpec SPEC = new DatasetSpec("t", List.of(
            Column.of("id", ParquetType.UUID_STR),
            Column.of("n", ParquetType.INT),
            Column.of("big", ParquetType.LONG),
            Column.of("flag", ParquetType.BOOL),
            Column.of("ts", ParquetType.TIMESTAMP),
            Column.of("note", ParquetType.STRING),
            Column.of("blob", ParquetType.JSON)));

    private static Map<String, Object> row(String id, Integer n, Long big, Boolean flag, Instant ts,
                                           String note, String blob) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("n", n); m.put("big", big); m.put("flag", flag);
        m.put("ts", ts); m.put("note", note); m.put("blob", blob);
        // lineage columns intentionally omitted -> written as NULL
        return m;
    }

    @Test
    void writesAndReadsAllTypesWithNulls() throws Exception {
        Path dir = Files.createTempDirectory("pqtest");
        Path file = dir.resolve("part.parquet");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("7f000101-9f06-1fdc-819f-a50301271752", 42, 9000000000L, true,
                Instant.parse("2026-09-16T01:00:00Z"), "hello", "[]"));
        rows.add(row("7f000101-0000-0000-0000-000000000001", null, null, false, null, null, null));

        long written = DuckDbParquetWriter.write(SPEC, rows, file);
        assertEquals(2, written);
        assertTrue(Files.exists(file));

        try (Connection c = DriverManager.getConnection("jdbc:duckdb:"); Statement s = c.createStatement()) {
            String p = file.toAbsolutePath().toString().replace('\\', '/');
            // predicate pushdown + projection: only n=42 row, only two columns
            try (ResultSet rs = s.executeQuery("SELECT id, ts FROM read_parquet('" + p + "') WHERE n = 42")) {
                assertTrue(rs.next());
                assertEquals("7f000101-9f06-1fdc-819f-a50301271752", rs.getString("id"));
                assertTrue(rs.getString("ts").startsWith("2026-09-16 01:00:00")); // UTC wall clock preserved
                assertTrue(!rs.next());
            }
            // NULL row round-trips as SQL NULL
            try (ResultSet rs = s.executeQuery("SELECT n, big, flag, ts, note FROM read_parquet('" + p
                    + "') WHERE flag = false")) {
                assertTrue(rs.next());
                rs.getInt("n"); assertTrue(rs.wasNull());
                assertNull(rs.getString("note"));
                assertNull(rs.getString("ts"));
            }
            // lineage columns exist in the schema (written as NULL)
            try (ResultSet rs = s.executeQuery("SELECT count(record_hash) hc, count(*) c FROM read_parquet('"
                    + p + "')")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt("hc")); // all null here
                assertEquals(2, rs.getInt("c"));
            }
        }
    }

    @Test
    void emptyDatasetWritesSchemaWithZeroRows() throws Exception {
        Path dir = Files.createTempDirectory("pqtest-empty");
        Path file = dir.resolve("part.parquet");
        long written = DuckDbParquetWriter.write(SPEC, List.of(), file);
        assertEquals(0, written);
        assertTrue(Files.exists(file));
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:"); Statement s = c.createStatement()) {
            String p = file.toAbsolutePath().toString().replace('\\', '/');
            try (ResultSet rs = s.executeQuery("SELECT count(*) c FROM read_parquet('" + p + "')")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt("c"));
            }
            // schema still present: describe should list business + 12 lineage columns
            try (ResultSet rs = s.executeQuery("SELECT count(*) c FROM (DESCRIBE SELECT * FROM read_parquet('"
                    + p + "'))")) {
                assertTrue(rs.next());
                assertEquals(SPEC.allColumns().size(), rs.getInt("c"));
            }
        }
    }
}
