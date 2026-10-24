package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.config.SchemaName;
import com.keyforge.iiq.event.EventFingerprint;
import com.keyforge.iiq.parquet.DuckDbParquetWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
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
 *
 * <p>Lineage on rerun: an UNCHANGED record keeps the {@code extracted_at} / {@code extraction_run_id} of the run that
 * first captured its current content, so those fields mean "when this version was captured"; NEW and CHANGED records
 * get the current run's values. {@code src_created} / {@code src_modified} carry the source table's
 * {@code created_at} / {@code modified_at} (when it has them) and {@code src_event_ts} the occurrence time of event
 * datasets, so date-range queries work on these TIMESTAMP columns. None of these are part of the content hash.
 */
public final class NativeParquetService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NativeParquetService.class);

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

    /** What the previous stable file holds for one record: its content hash and when that version was captured. */
    record Prior(String hash, Object extractedAt, String runId) {
    }

    /**
     * The {@code extracted_at} to write: an UNCHANGED record keeps the time its current content was first captured;
     * NEW / CHANGED records (or a prior file without the value) get the current run's time.
     */
    static Object extractedAtFor(Change change, Prior prior, Instant now) {
        return change == Change.UNCHANGED && prior != null && prior.extractedAt() != null ? prior.extractedAt() : now;
    }

    /** The {@code extraction_run_id} to write, with the same rule as {@link #extractedAtFor}. */
    static String runIdFor(Change change, Prior prior, String runId) {
        return change == Change.UNCHANGED && prior != null && prior.runId() != null ? prior.runId() : runId;
    }

    /** Source tables whose rows ARE events: their {@code created_at} is the event's occurrence time. */
    static final java.util.Set<String> EVENT_SOURCE_TABLES = java.util.Set.of("kf_audit_event");

    private final String schema;

    public NativeParquetService(String schema) {
        this.schema = SchemaName.validate(schema);
    }

    public List<Result> run(Connection pg, Path outputDir, String runId) {
        return run(new PostgresParquetRowSource(pg, schema), outputDir, runId, NativeParquetDatasets.all());
    }

    /** Full current-state export using the KF Agent services as the source. */
    public List<Result> runAgent(com.keyforge.iiq.client.IiqSessionClient session, Path outputDir,
                                 String runId, String onlyDataset) {
        List<NativeParquetDatasets.Def> selected = NativeParquetDatasets.all();
        if (onlyDataset != null) {
            selected = selected.stream().filter(d -> d.parquetName().equals(onlyDataset)).toList();
            if (selected.isEmpty()) {
                return List.of(new Result(onlyDataset, 0, 0, 0, 0, null, "unknown dataset"));
            }
        }
        return run(new AgentParquetRowSource(session), outputDir, runId, selected);
    }

    public List<Result> run(ParquetRowSource source, Path outputDir, String runId) {
        return run(source, outputDir, runId, NativeParquetDatasets.all());
    }

    private List<Result> run(ParquetRowSource source, Path outputDir, String runId,
                             List<NativeParquetDatasets.Def> datasets) {
        Instant extractedAt = Instant.now();
        List<Result> results = new ArrayList<>();
        for (NativeParquetDatasets.Def d : datasets) {
            try {
                results.add(runOne(source, d, outputDir, runId, extractedAt));
            } catch (Exception e) {
                results.add(new Result(d.parquetName(), 0, 0, 0, 0, null,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return results;
    }

    Result runOne(ParquetRowSource source, NativeParquetDatasets.Def d, Path outputDir, String runId,
                  Instant extractedAt) throws Exception {
        Path file = outputDir.resolve(d.parquetName()).resolve("current.parquet");

        // 1) prior content hashes + capture times (by PK), for change classification and lineage carry-over.
        Map<String, Prior> prior = readPrior(file);

        // 2) read native current-state rows and build content-hashed dataset rows.
        List<Map<String, Object>> rows = new ArrayList<>();
        int inserted = 0, changed = 0, unchanged = 0;
        boolean isEvent = EVENT_SOURCE_TABLES.contains(d.sourceTable());
        for (ParquetRowSource.Row sourceRow : source.read(d)) {
            String pk = sourceRow.primaryKey();
            Map<String, Object> row = new LinkedHashMap<>();
            String[] parts = new String[d.businessColumns().size()];
            for (int i = 0; i < d.businessColumns().size(); i++) {
                String c = d.businessColumns().get(i);
                Object value = sourceRow.businessValues().get(c);
                String v = value == null ? null : value.toString();
                row.put(c, v);
                parts[i] = v;
            }
            String contentHash = EventFingerprint.fingerprint(parts);   // business-only; no timestamp

            Prior before = prior.get(pk);
            Change change = classify(before == null ? null : before.hash(), contentHash);
            switch (change) {
                case NEW -> inserted++;
                case CHANGED -> changed++;
                case UNCHANGED -> unchanged++;
            }

            Instant created = sourceRow.created();
            Instant modified = sourceRow.modified();
            stampLineage(row, d, pk, contentHash, runIdFor(change, before, runId),
                    extractedAtFor(change, before, extractedAt), created, modified, isEvent ? created : null);
            rows.add(row);
        }

        // 3) write the single stable current-state file (overwrite). No per-run accumulation.
        long written = DuckDbParquetWriter.write(d.spec(), rows, file);
        return new Result(d.parquetName(), (int) written, inserted, changed, unchanged,
                file.toAbsolutePath().toString(), null);
    }

    /**
     * Reads {pk → (record_hash, extracted_at, extraction_run_id)} from the prior stable Parquet file (empty if it
     * does not exist). A prior file that cannot be read (e.g. damaged before writes became atomic) is reported and
     * treated as absent: every record is then classified NEW and the file is rebuilt from the source.
     */
    static Map<String, Prior> readPrior(Path file) {
        Map<String, Prior> map = new HashMap<>();
        if (!Files.exists(file)) {
            return map;
        }
        String path = file.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
        // Dedup key = src_natural_key (the record PK) — always unique and never a business column, unlike
        // src_object_id which a dataset (kf_event_link) may legitimately use for its own endpoint.
        try (Connection duck = DriverManager.getConnection("jdbc:duckdb:");
             Statement st = duck.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + SRC_NATURAL_KEY + ", " + RECORD_HASH + ", " + EXTRACTED_AT
                     + ", " + EXTRACTION_RUN_ID + " FROM read_parquet('" + path + "')")) {
            while (rs.next()) {
                java.sql.Timestamp ts = rs.getTimestamp(3);
                LocalDateTime capturedAt = ts == null ? null : ts.toLocalDateTime(); // UTC wall clock, as written
                map.put(rs.getString(1), new Prior(rs.getString(2), capturedAt, rs.getString(4)));
            }
        } catch (SQLException unreadable) {
            LOG.warn("[native-parquet] prior file {} is unreadable ({}); rebuilding it from the source",
                    file.toAbsolutePath(), unreadable.getMessage());
            map.clear();
        }
        return map;
    }

    /**
     * Stamps the lineage envelope. {@code src_natural_key} carries the record PK (the dedup identity). A
     * lineage key that collides with a business column (e.g. an event-link's own {@code src_object_id}) is
     * NOT overwritten — the business value wins, matching the deduped physical schema.
     */
    private void stampLineage(Map<String, Object> row, NativeParquetDatasets.Def d, String pk,
                              String contentHash, String runId, Object extractedAt, Instant srcCreated,
                              Instant srcModified, Instant srcEventTs) {
        java.util.Set<String> biz = new java.util.HashSet<>(d.businessColumns());
        putUnlessBusiness(row, biz, SRC_SYSTEM, "IdentityIQ");
        putUnlessBusiness(row, biz, SRC_OBJECT_TYPE, d.srcObjectType());
        putUnlessBusiness(row, biz, SRC_OBJECT_ID, pk);
        row.put(SRC_NATURAL_KEY, pk);            // dedup key — guaranteed non-business
        putUnlessBusiness(row, biz, SRC_CREATED, srcCreated);
        putUnlessBusiness(row, biz, SRC_MODIFIED, srcModified);
        putUnlessBusiness(row, biz, SRC_EVENT_TS, srcEventTs);
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

}
