package com.keyforge.iiq.parquet;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Runtime configuration for the Parquet workstream. Output directory is resolved from the environment
 * (env {@code PARQUET_OUT_DIR}, then JVM system property, then default {@code ./parquet-data}) using
 * the same env-first convention as {@link com.keyforge.iiq.config.AppConfig}. IIQ connection settings
 * come from {@code AppConfig}; nothing here depends on PostgreSQL.
 */
public final class ParquetConfig {

    public static final String KEY_OUT_DIR = "PARQUET_OUT_DIR";
    public static final String DEFAULT_OUT_DIR = "parquet-data";

    private final Path outputDir;

    public ParquetConfig(Path outputDir) {
        this.outputDir = outputDir;
    }

    public static ParquetConfig load() {
        String v = System.getenv(KEY_OUT_DIR);
        if (v == null || v.isBlank()) {
            v = System.getProperty(KEY_OUT_DIR);
        }
        if (v == null || v.isBlank()) {
            v = DEFAULT_OUT_DIR;
        }
        return new ParquetConfig(Path.of(v.trim()));
    }

    /** Explicit output setting only; unlike {@link #load()}, this never selects the default directory. */
    public static Optional<Path> explicitOutputDir() {
        return explicitOutputDir(System.getenv(KEY_OUT_DIR), System.getProperty(KEY_OUT_DIR));
    }

    static Optional<Path> explicitOutputDir(String environment, String systemProperty) {
        String value = environment;
        if (value == null || value.isBlank()) value = systemProperty;
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(Path.of(value.trim()));
    }

    public Path outputDir() {
        return outputDir;
    }

    /** Append-oriented layout: one part file per extraction run under the dataset directory. */
    public Path datasetFile(String dataset, String extractionRunId) {
        return outputDir.resolve(dataset).resolve("part-" + extractionRunId + ".parquet");
    }
}
