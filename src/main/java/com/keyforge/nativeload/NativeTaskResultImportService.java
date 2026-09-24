package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native TaskResult import: pull the plugin endpoint page-by-page (via a
 * {@link NativeTaskResultPageSource}), parse each page, and upsert every record (via a
 * {@link NativeTaskResultSink}). Pure orchestration — no HTTP and no JDBC — unit-testable with fakes.
 * Mirrors the validated native imports: confirmed-full-scan deletion sweep with the empty-source safety
 * guard (the shared {@link SoftDeleteSweeper} never sweeps against an empty keep-set).
 */
public final class NativeTaskResultImportService {

    public static final class Result {
        private final int extracted;
        private final int inserted;
        private final int updated;
        private final int failed;
        private final int marked;
        private final int revived;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int extracted, int inserted, int updated, int failed,
               int marked, int revived, boolean sweepRan, boolean sweepSkipped, List<String> failures) {
            this.extracted = extracted;
            this.inserted = inserted;
            this.updated = updated;
            this.failed = failed;
            this.marked = marked;
            this.revived = revived;
            this.sweepRan = sweepRan;
            this.sweepSkipped = sweepSkipped;
            this.failures = failures;
        }

        public int getExtracted() { return extracted; }
        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getFailed() { return failed; }
        public int getPersisted() { return inserted + updated; }
        public int getMarkedDeleted() { return marked; }
        public int getRevived() { return revived; }
        public boolean isSweepRan() { return sweepRan; }
        public boolean isSweepSkipped() { return sweepSkipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeTaskResultPageSource source;
    private final NativeTaskResultParser parser = new NativeTaskResultParser();
    private final NativeTaskResultSink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeTaskResultImportService(NativeTaskResultPageSource source, NativeTaskResultSink sink,
                                         int pageSize, boolean sweepDeletions) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 100;
        this.sweepDeletions = sweepDeletions;
    }

    public Result importAll() throws SQLException {
        sink.ensure();

        int extracted = 0;
        int inserted = 0;
        int updated = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        Set<String> keep = new LinkedHashSet<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            List<NativeTaskResultRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeTaskResultRecord rec : page) {
                extracted++;
                keep.add(NativeTaskResultRepository.canonicalTaskResultId(rec));
                try {
                    NativeTaskResultRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativeTaskResultRepository.UpsertOutcome.INSERTED) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.name != null ? rec.name : rec.sourceId) + ": " + e.getMessage());
                    }
                }
            }
            if (page.size() < pageSize) {
                break; // scan complete
            }
            start += pageSize;
        }

        int marked = 0;
        int revived = 0;
        boolean sweepRan = false;
        boolean sweepSkipped = false;
        if (sweepDeletions) {
            SoftDeleteSweeper.SweepResult sweep = sink.sweep(keep);
            sweepRan = true;
            sweepSkipped = sweep.skipped();
            marked = sweep.marked();
            revived = sweep.revived();
        }

        return new Result(extracted, inserted, updated, failed, marked, revived, sweepRan, sweepSkipped, failures);
    }
}
