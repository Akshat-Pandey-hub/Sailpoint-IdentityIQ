package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.parquet.DuckDbParquetWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end {@link NativeParquetService} reruns over a fake native PostgreSQL source and REAL DuckDB Parquet files:
 * no duplicates, unchanged records keep the capture time and run id of their current version (P1), source
 * created / modified / event times are filled (P2), and files are replaced safely, with an unreadable prior file
 * rebuilt instead of failing (P3).
 */
class NativeParquetRerunTest {

    private static final OffsetDateTime C1 = OffsetDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime M1 = OffsetDateTime.of(2026, 10, 2, 9, 0, 0, 0, ZoneOffset.UTC);

    /** Native source rows per table; columns not given read as NULL. */
    private static final class Pg {
        final Map<String, List<Map<String, Object>>> tables = new HashMap<>();
        final Map<String, List<String>> extraColumns = new HashMap<>(); // table -> is_deleted / created_at / ...

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "createStatement" -> statement();
                        case "prepareStatement" -> columnCheck();
                        default -> defaultFor(m);
                    });
        }

        private PreparedStatement columnCheck() {
            Map<Integer, Object> bound = new HashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                        if (m.getName().equals("setString")) {
                            bound.put((Integer) a[0], a[1]);
                            return null;
                        }
                        if (m.getName().equals("executeQuery")) {
                            boolean has = extraColumns.getOrDefault((String) bound.get(2), List.of())
                                    .contains((String) bound.get(3));
                            return rows(has ? List.of(Map.of()) : List.of());
                        }
                        return defaultFor(m);
                    });
        }

        private Statement statement() {
            return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
                    (p, m, a) -> {
                        if (m.getName().equals("executeQuery")) {
                            String sql = (String) a[0];
                            String table = sql.substring(sql.indexOf(" FROM s.") + 8).split(" ")[0];
                            return rows(tables.getOrDefault(table, List.of()));
                        }
                        return defaultFor(m);
                    });
        }

        private static ResultSet rows(List<Map<String, Object>> rows) {
            int[] pos = {-1};
            return (ResultSet) Proxy.newProxyInstance(Pg.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "next" -> ++pos[0] < rows.size();
                        case "getString" -> {
                            Object v = rows.get(pos[0]).get((String) a[0]);
                            yield v == null ? null : v.toString();
                        }
                        case "getObject" -> rows.get(pos[0]).get((String) a[0]);
                        default -> defaultFor(m);
                    });
        }

        private static Object defaultFor(Method m) {
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return Boolean.FALSE;
            if (r == int.class) return 0;
            return null;
        }
    }

    private static Map<String, Object> audit(String id, String action) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("__pk", id);
        r.put("source_id", "src-" + id);
        r.put("action", action);
        r.put("created_at", C1.toString());
        r.put("__src_created", C1);
        return r;
    }

    private static Map<String, Object> request(String id, String state) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("__pk", id);
        r.put("name", "req-" + id);
        r.put("state", state);
        r.put("__src_created", C1);
        r.put("__src_modified", M1);
        return r;
    }

    private static Pg source() {
        Pg pg = new Pg();
        pg.extraColumns.put("kf_audit_event", List.of("created_at"));
        pg.extraColumns.put("kf_identity_request", List.of("is_deleted", "created_at", "modified_at"));
        pg.tables.put("kf_audit_event", new ArrayList<>(List.of(audit("a1", "login"), audit("a2", "logout"))));
        pg.tables.put("kf_identity_request", new ArrayList<>(List.of(request("r1", "Pending"))));
        return pg;
    }

    /** Reads {pk -> {extraction_run_id, extracted_at, src_created, src_modified, src_event_ts}} from a dataset file. */
    private static Map<String, Object[]> read(Path dir, String dataset) throws Exception {
        String path = dir.resolve(dataset).resolve("current.parquet").toAbsolutePath().toString().replace('\\', '/');
        Map<String, Object[]> out = new HashMap<>();
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:"); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT src_natural_key, extraction_run_id, extracted_at, src_created, "
                     + "src_modified, src_event_ts FROM read_parquet('" + path + "')")) {
            while (rs.next()) {
                out.put(rs.getString(1), new Object[]{rs.getString(2), ts(rs, 3), ts(rs, 4), ts(rs, 5), ts(rs, 6)});
            }
        }
        return out;
    }

    private static LocalDateTime ts(ResultSet rs, int i) throws Exception {
        java.sql.Timestamp t = rs.getTimestamp(i);
        return t == null ? null : t.toLocalDateTime();
    }

    private static NativeParquetService.Result result(List<NativeParquetService.Result> all, String dataset) {
        return all.stream().filter(r -> r.dataset().equals(dataset)).findFirst().orElseThrow();
    }

    @Test
    void rerunAddsNoDuplicatesAndUnchangedRecordsKeepTheirCaptureTimeAndRun(@TempDir Path dir) throws Exception {
        Pg pg = source();
        NativeParquetService svc = new NativeParquetService("s");

        NativeParquetService.Result first = result(svc.run(pg.connection(), dir, "run-1"), "kf_audit_event");
        assertEquals(2, first.total());
        assertEquals(2, first.inserted());
        Map<String, Object[]> v1 = read(dir, "kf_audit_event");
        Thread.sleep(5); // a later run must not overwrite the capture time of unchanged rows

        NativeParquetService.Result second = result(svc.run(pg.connection(), dir, "run-2"), "kf_audit_event");
        assertEquals(2, second.total(), "a rerun never adds a second copy");
        assertEquals(0, second.inserted());
        assertEquals(2, second.unchanged());
        Map<String, Object[]> v2 = read(dir, "kf_audit_event");
        for (String pk : List.of("a1", "a2")) {
            assertEquals("run-1", v2.get(pk)[0], pk + " keeps the run that captured its content");
            assertEquals(v1.get(pk)[1], v2.get(pk)[1], pk + " keeps its capture time");
        }
    }

    @Test
    void onlyTheChangedRecordGetsTheNewRunAndTime(@TempDir Path dir) throws Exception {
        Pg pg = source();
        NativeParquetService svc = new NativeParquetService("s");
        svc.run(pg.connection(), dir, "run-1");
        Map<String, Object[]> v1 = read(dir, "kf_audit_event");
        Thread.sleep(5);

        pg.tables.get("kf_audit_event").set(1, audit("a2", "logout-changed"));
        pg.tables.get("kf_audit_event").add(audit("a3", "login"));
        NativeParquetService.Result r = result(svc.run(pg.connection(), dir, "run-2"), "kf_audit_event");
        assertEquals(1, r.inserted());
        assertEquals(1, r.changed());
        assertEquals(1, r.unchanged());

        Map<String, Object[]> v2 = read(dir, "kf_audit_event");
        assertEquals("run-1", v2.get("a1")[0]);
        assertEquals(v1.get("a1")[1], v2.get("a1")[1]);
        assertEquals("run-2", v2.get("a2")[0], "a changed record is a new version");
        assertNotEquals(v1.get("a2")[1], v2.get("a2")[1]);
        assertEquals("run-2", v2.get("a3")[0]);
    }

    @Test
    void sourceTimesAreFilledAndEventTimeOnlyForEvents(@TempDir Path dir) throws Exception {
        new NativeParquetService("s").run(source().connection(), dir, "run-1");
        LocalDateTime created = C1.toLocalDateTime();
        LocalDateTime modified = M1.toLocalDateTime();

        Object[] auditRow = read(dir, "kf_audit_event").get("a1");
        assertEquals(created, auditRow[2], "src_created");
        assertNull(auditRow[3], "the audit table has no modified_at");
        assertEquals(created, auditRow[4], "src_event_ts = the audit event's occurrence time");

        Object[] requestRow = read(dir, "kf_access_request").get("r1");
        assertEquals(created, requestRow[2]);
        assertEquals(modified, requestRow[3]);
        assertNull(requestRow[4], "an identity request is not an event dataset");
    }

    @Test
    void anUnreadablePriorFileIsRebuiltNotFatal(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("kf_audit_event").resolve("current.parquet");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not a parquet file");

        NativeParquetService.Result r = result(new NativeParquetService("s").run(source().connection(), dir, "run-1"),
                "kf_audit_event");
        assertNull(r.error());
        assertEquals(2, r.inserted(), "treated as no prior file");
        assertEquals(2, read(dir, "kf_audit_event").size(), "the file is valid again");
    }

    @Test
    void aFailedWriteKeepsThePreviousFileAndLeavesNoTempFile(@TempDir Path dir) throws Exception {
        Pg pg = source();
        new NativeParquetService("s").run(pg.connection(), dir, "run-1");
        Path file = dir.resolve("kf_audit_event").resolve("current.parquet");
        byte[] before = Files.readAllBytes(file);

        NativeParquetDatasets.Def audit = NativeParquetDatasets.all().stream()
                .filter(d -> d.parquetName().equals("kf_audit_event")).findFirst().orElseThrow();
        Map<String, Object> bad = new HashMap<>();
        bad.put("src_natural_key", "x");
        bad.put("extracted_at", "not-a-timestamp"); // rejected while staging -> the write fails
        assertThrows(RuntimeException.class, () -> DuckDbParquetWriter.write(audit.spec(), List.of(bad), file));

        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(file)), "previous file untouched");
        try (Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(List.of("current.parquet"), files.map(p -> p.getFileName().toString()).toList(),
                    "no temporary file left behind");
        }
    }

    @Test
    void carryOverRules() {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");
        LocalDateTime then = LocalDateTime.of(2026, 10, 1, 0, 0);
        NativeParquetService.Prior prior = new NativeParquetService.Prior("h", then, "run-1");
        assertEquals(then, NativeParquetService.extractedAtFor(NativeParquetService.Change.UNCHANGED, prior, now));
        assertEquals("run-1", NativeParquetService.runIdFor(NativeParquetService.Change.UNCHANGED, prior, "run-2"));
        assertEquals(now, NativeParquetService.extractedAtFor(NativeParquetService.Change.CHANGED, prior, now));
        assertEquals("run-2", NativeParquetService.runIdFor(NativeParquetService.Change.NEW, null, "run-2"));
        assertEquals(now, NativeParquetService.extractedAtFor(NativeParquetService.Change.UNCHANGED,
                new NativeParquetService.Prior("h", null, null), now), "a prior file without the value");
    }
}
