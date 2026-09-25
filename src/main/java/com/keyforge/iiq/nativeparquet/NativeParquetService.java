package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.event.EventFingerprint;
import com.keyforge.iiq.parquet.DuckDbParquetWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native PostgreSQL &rarr; Parquet, current-state, content-hash deduplicated. For each approved dataset it
 * reads the native {@code iiq_native} table (active rows only), computes a content hash over the business
 * columns (the extraction timestamp/run id are NEVER part of the hash), compares each row to the prior
 * stable Parquet file by PK, and rewrites ONE stable file (no per-run versioning, no SCD2). Because the
 * native tables are already one-row-per-PK current-state, the Parquet is inherently deduplicated: a rerun
 * with no source change reproduces the same content hashes and the same row count.
 */
public final class NativeParquetService {

    /** Lineage envelope keys (must match com.keyforge.iiq.parquet.Lineage). */
    static final String SRC_SYSTEM = "src_system";
    static final String SRC_OBJECT_TYPE = "src_object_type";
    static final String SRC_OBJECT_ID = "src_object_id";
    static final String SRC_NATURAL_KEY = "src_natural_key";
    static final String SRC_CREATED = "src_created";
    static final String SRC_MODIFIED = "src_modified";
    static final String SRC_EVENT_TS = "src_event_ts";
    static final String EXTRACTED_AT = "extracted_at";
    static final String EXTRACTION_RUN_ID = "extraction_run_id";
    static final String SRC_INTERFACE = "src_interface";
    static final String RECORD_HASH = "record_hash";
    static final String RAW_REF = "raw_ref";

    public record Result(String dataset, int total, int inserted, int changed, int unchanged,
                         String file, String error) {
    }

    /** Change classification for one record against its prior content hash. */
    public enum Change { NEW, CHANGED, UNCHANGED }

    /**
     * Classifies a record by comparing its content hash to the prior stable-file hash for the same PK.
     * A different extraction timestamp alone never reaches here because the timestamp is not in the hash.
     */
    public static Change classify(String priorHash, String contentHash) {
        if (priorHash == null) {
            return Change.NEW;
        }
        return priorHash.equals(contentHash) ? Change.UNCHANGED : Change.CHANGED;
    }

    private final String schema;

    public NativeParquetService(String schema) {
        this.schema = SchemaName.validate(schema);
    }

    public List<Result> run(Connection pg, Path outputDir, String runId) {
        Instant extractedAt = Instant.now();
        List<Result> results = new ArrayList<>();
        for (NativeParquetDatasets.Def d : NativeParquetDatasets.all()) {
            try {
                results.add(runOne(pg, d, outputDir, runId, extractedAt));
            } catch (Exception e) {
                results.add(new Result(d.parquetName(), 0, 0, 0, 0, null,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return results;
    }

    private Result runOne(Connection pg, NativeParquetDatasets.Def d, Path outputDir, String runId,
                          Instant extractedAt) throws SQLException {
        Path file = outputDir.resolve(d.parquetName()).resolve("current.parquet");

        // 1) prior content hashes (by PK), for change classification.
        Map<String, String> prior = readPriorHashes(file);

        // 2) read native current-state rows and build content-hashed dataset rows.
        List<Map<String, Object>> rows = new ArrayList<>();
        int inserted = 0, changed = 0, unchanged = 0;
        boolean hasSoftDelete = columnExists(pg, d.sourceTable(), "is_deleted");
        String cols = String.join(", ", d.businessColumns());
        String sql = "SELECT " + d.pkColumn() + " AS __pk, " + cols + " FROM " + schema + "." + d.sourceTable()
                + (hasSoftDelete ? " WHERE is_deleted = false" : "");
        try (Statement st = pg.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String pk = rs.getString("__pk");
                Map<String, Object> row = new LinkedHashMap<>();
                String[] parts = new String[d.businessColumns().size()];
                for (int i = 0; i < d.businessColumns().size(); i++) {
                    String c = d.businessColumns().get(i);
                    String v = rs.getString(c);   // stable PG text form (deterministic across runs)
                    row.put(c, v);
                    parts[i] = v;
                }
                String contentHash = EventFingerprint.fingerprint(parts);   // business-only; no timestamp

                switch (classify(prior.get(pk), contentHash)) {
                    case NEW -> inserted++;
                    case CHANGED -> changed++;
                    case UNCHANGED -> unchanged++;
                }

                stampLineage(row, d, pk, contentHash, runId, extractedAt);
                rows.add(row);
            }
        }

        // 3) write the single stable current-state file (overwrite). No per-run accumulation.
        long written = DuckDbParquetWriter.write(d.spec(), rows, file);
        return new Result(d.parquetName(), (int) written, inserted, changed, unchanged,
                file.toAbsolutePath().toString(), null);
    }

    /** Reads {pk → record_hash} from a prior stable Parquet file (empty if the file does not exist). */
    private Map<String, String> readPriorHashes(Path file) throws SQLException {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(file)) {
            return map;
        }
        String path = file.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
        // Dedup key = src_natural_key (the record PK) — always unique and never a business column, unlike
        // src_object_id which a dataset (kf_event_link) may legitimately use for its own endpoint.
        try (Connection duck = DriverManager.getConnection("jdbc:duckdb:");
             Statement st = duck.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT " + SRC_NATURAL_KEY + ", " + RECORD_HASH + " FROM read_parquet('" + path + "')")) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        }
        return map;
    }

    /**
     * Stamps the lineage envelope. {@code src_natural_key} carries the record PK (the dedup identity). A
     * lineage key that collides with a business column (e.g. an event-link's own {@code src_object_id}) is
     * NOT overwritten — the business value wins, matching the deduped physical schema.
     */
    private void stampLineage(Map<String, Object> row, NativeParquetDatasets.Def d, String pk,
                              String contentHash, String runId, Instant extractedAt) {
        java.util.Set<String> biz = new java.util.HashSet<>(d.businessColumns());
        putUnlessBusiness(row, biz, SRC_SYSTEM, "IdentityIQ");
        putUnlessBusiness(row, biz, SRC_OBJECT_TYPE, d.srcObjectType());
        putUnlessBusiness(row, biz, SRC_OBJECT_ID, pk);
        row.put(SRC_NATURAL_KEY, pk);            // dedup key — guaranteed non-business
        putUnlessBusiness(row, biz, SRC_CREATED, null);
        putUnlessBusiness(row, biz, SRC_MODIFIED, null);
        putUnlessBusiness(row, biz, SRC_EVENT_TS, null);
        putUnlessBusiness(row, biz, EXTRACTED_AT, extractedAt);
        putUnlessBusiness(row, biz, EXTRACTION_RUN_ID, runId);
        putUnlessBusiness(row, biz, SRC_INTERFACE, "native_iiq_java_api");
        putUnlessBusiness(row, biz, RECORD_HASH, contentHash);
        putUnlessBusiness(row, biz, RAW_REF, null);
    }

    private static void putUnlessBusiness(Map<String, Object> row, java.util.Set<String> biz, String key,
                                          Object value) {
        if (!biz.contains(key)) {
            row.put(key, value);
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
