package com.keyforge.iiq.raw;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Runtime configuration for the immutable RAW zone. Follows the same env-first precedence as
 * {@link com.keyforge.iiq.config.AppConfig} / {@code ParquetConfig}: environment variable
 * {@code RAW_OUT_DIR} first, then the JVM system property of the same name.
 *
 * <p><b>Enablement:</b> unlike the Parquet output dir, RAW has <b>no default directory</b> — it is
 * enabled <i>only</i> when {@code RAW_OUT_DIR} is explicitly set. When it is unset, {@link
 * #loadIfEnabled()} returns {@link Optional#empty()} and no RAW capture occurs, so existing extraction
 * behavior is preserved byte-for-byte. This deliberately avoids a separate enable/disable flag: the
 * presence of the output directory is the switch (per the approved design).
 */
public final class RawConfig {

    public static final String KEY_OUT_DIR = "RAW_OUT_DIR";

    private final Path outputDir;

    public RawConfig(Path outputDir) {
        this.outputDir = outputDir;
    }

    /** Returns a config only when {@code RAW_OUT_DIR} is explicitly set (env, then system property). */
    public static Optional<RawConfig> loadIfEnabled() {
        String v = System.getenv(KEY_OUT_DIR);
        if (v == null || v.isBlank()) {
            v = System.getProperty(KEY_OUT_DIR);
        }
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RawConfig(Path.of(v.trim())));
    }

    public Path outputDir() {
        return outputDir;
    }

    /** Directory holding one {@code <extraction_run_id>.jsonl} provenance manifest per run. */
    public Path runsDir() {
        return outputDir.resolve("_runs");
    }
}
