package com.keyforge.iiq.parquet;

import com.keyforge.iiq.config.AppConfig;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates a direct IIQ &rarr; Parquet extraction run: generates one {@code extraction_run_id},
 * extracts each requested dataset from IIQ (reusing the existing Services/mappers), writes a Parquet
 * part file, and reports counts. PostgreSQL is never touched.
 */
public final class ParquetExtractionService {

    /** Datasets known to be source-limited on this instance (schema written, zero rows expected). */
    private static final Map<String, String> SOURCE_LIMITED = Map.of(
            "kf_cert_item_decision",
            "certification decision hierarchy not reachable read-only (ui drill-in 500, no plugin)");

    public record DatasetResult(String dataset, int extracted, long written, Path file,
                                String note, String error) {
        public int failed() {
            return error == null ? (int) (extracted - written) : extracted;
        }
    }

    public record RunResult(String runId, Path outputDir, List<DatasetResult> datasets) {
    }

    private final ParquetDatasets datasets = new ParquetDatasets();

    public List<String> allDatasetNames() {
        return datasets.names();
    }

    public RunResult extract(List<String> datasetNames, AppConfig iiqConfig, ParquetConfig pq, boolean debugHttp) {
        String runId = UUID.randomUUID().toString();
        Instant extractedAt = Instant.now();
        ExtractionContext ctx = new ExtractionContext(iiqConfig, runId, extractedAt, debugHttp);

        List<DatasetResult> results = new ArrayList<>();
        for (String name : datasetNames) {
            ParquetDatasets.Entry entry = datasets.get(name);
            if (entry == null) {
                results.add(new DatasetResult(name, 0, 0, null, null, "unknown dataset"));
                continue;
            }
            try {
                List<Map<String, Object>> rows = entry.extractor().rows(ctx);
                Path file = pq.datasetFile(name, runId);
                long written = DuckDbParquetWriter.write(entry.spec(), rows, file);
                results.add(new DatasetResult(name, rows.size(), written, file, SOURCE_LIMITED.get(name), null));
            } catch (Exception e) {
                results.add(new DatasetResult(name, 0, 0, null, SOURCE_LIMITED.get(name),
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return new RunResult(runId, pq.outputDir(), results);
    }
}
