package com.keyforge.iiq.nativeparquet;

import com.keyforge.iiq.parquet.ParquetConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native resolver reads ONLY {@code <dataset>/current.parquet} and NEVER a REST/SCIM
 * {@code part-<run>.parquet}, even when part files coexist in the same dataset directory. This is the
 * isolation guarantee that keeps native query endpoints from ever serving REST/SCIM-sourced rows.
 */
class NativeParquetFileResolverTest {

    @TempDir
    Path outDir;

    private NativeParquetFileResolver resolver() {
        return new NativeParquetFileResolver(new ParquetConfig(outDir));
    }

    private Path datasetDir(String dataset) throws Exception {
        Path dir = outDir.resolve(dataset);
        Files.createDirectories(dir);
        return dir;
    }

    @Test
    void emptyWhenNothingExtracted() {
        assertTrue(resolver().resolve("task_result", null).isEmpty());
    }

    @Test
    void ignoresPartFilesAndReturnsEmptyWhenOnlyPartFilesExist() throws Exception {
        Path dir = datasetDir("task_result");
        Files.writeString(dir.resolve("part-run-aaaa.parquet"), "decoy");
        Files.writeString(dir.resolve("part-run-bbbb.parquet"), "decoy");
        // No current.parquet → nothing to serve; the part files must NOT be picked up.
        assertTrue(resolver().resolve("task_result", null).isEmpty());
    }

    @Test
    void returnsCurrentParquetWhenPresent() throws Exception {
        Path dir = datasetDir("task_result");
        Path current = dir.resolve("current.parquet");
        Files.writeString(current, "native");
        Optional<Path> r = resolver().resolve("task_result", null);
        assertTrue(r.isPresent());
        assertEquals("current.parquet", r.get().getFileName().toString());
    }

    @Test
    void prefersCurrentAndIgnoresCoexistingPartFile() throws Exception {
        Path dir = datasetDir("kf_event_link");
        Files.writeString(dir.resolve("part-run-cccc.parquet"), "rest-scim-decoy");
        Path current = dir.resolve("current.parquet");
        Files.writeString(current, "native");
        Path resolved = resolver().resolve("kf_event_link", null).orElseThrow();
        assertEquals("current.parquet", resolved.getFileName().toString());
    }

    @Test
    void runArgumentIsIgnored_alwaysServesCurrentState() throws Exception {
        Path dir = datasetDir("kf_audit_event");
        Files.writeString(dir.resolve("current.parquet"), "native");
        // A ?run=<id> that would select a specific REST part file is not applicable to native datasets.
        Path resolved = resolver().resolve("kf_audit_event", "some-run-id").orElseThrow();
        assertEquals("current.parquet", resolved.getFileName().toString());
    }
}
