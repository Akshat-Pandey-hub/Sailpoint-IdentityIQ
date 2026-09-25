package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.parquet.ParquetConfig;
import com.keyforge.iiq.rest.FileResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the query file for a NATIVE-source dataset. Native datasets are written by
 * {@link NativeParquetService} as a single stable current-state file per dataset,
 * {@code <PARQUET_OUT_DIR>/<dataset>/current.parquet} (overwritten each run, content-hash deduplicated,
 * no per-run accumulation).
 *
 * <p><b>Isolation guarantee:</b> this resolver reads ONLY {@code current.parquet}. It never reads, globs,
 * or falls back to the REST/SCIM {@code part-<run>.parquet} snapshot files that may coexist in the same
 * dataset directory — so the native query endpoints can never serve REST/SCIM-sourced rows, even for a
 * dataset name that both pipelines share. There is no per-run history for native datasets, so a {@code run}
 * argument is not applicable and is intentionally ignored.
 */
public final class NativeParquetFileResolver implements FileResolver {

    /** The fixed, current-state file name written by the native Parquet pipeline. */
    public static final String CURRENT_FILE = "current.parquet";

    private final ParquetConfig config;

    public NativeParquetFileResolver(ParquetConfig config) {
        this.config = config;
    }

    @Override
    public Optional<Path> resolve(String dataset, String run) {
        Path file = config.outputDir().resolve(dataset).resolve(CURRENT_FILE);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }
}
