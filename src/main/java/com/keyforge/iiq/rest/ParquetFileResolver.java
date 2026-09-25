package com.keyforge.iiq.rest;

import com.keyforge.iiq.parquet.ParquetConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves which Parquet part file the REST service reads for a dataset.
 *
 * <p><b>Why not glob every part file:</b> every extractor writes a <i>full snapshot</i> of its dataset
 * per run (e.g. all audit events, all provisioning items) into {@code <dataset>/part-<run>.parquet}.
 * Reading all part files would therefore duplicate the same records across runs. So REST reads a
 * <b>single</b> file: by default the <b>most recently written</b> part file (the latest successful
 * run of that dataset), determined by file modification time — robust because the run id in the file
 * name is a random UUID and is not time-ordered. A specific run can be requested with {@code ?run=};
 * that reads exactly {@code part-<run>.parquet}.
 */
public final class ParquetFileResolver implements FileResolver {

    private final ParquetConfig config;

    public ParquetFileResolver(ParquetConfig config) {
        this.config = config;
    }

    /** The Parquet file to query, or empty if the dataset has no extracted part files yet. */
    @Override
    public Optional<Path> resolve(String dataset, String run) {
        if (run != null && !run.isBlank()) {
            Path specific = config.datasetFile(dataset, run.trim());
            return Files.isRegularFile(specific) ? Optional.of(specific) : Optional.empty();
        }
        Path dir = config.outputDir().resolve(dataset);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("part-") && n.endsWith(".parquet");
                    })
                    .max(Comparator.comparingLong(ParquetFileResolver::lastModified));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Lists available extraction-run ids for a dataset (from its part file names). */
    public List<String> runs(String dataset) {
        Path dir = config.outputDir().resolve(dataset);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("part-") && n.endsWith(".parquet"))
                    .map(n -> n.substring("part-".length(), n.length() - ".parquet".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }
}
