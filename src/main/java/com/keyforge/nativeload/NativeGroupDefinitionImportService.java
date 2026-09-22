package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native GroupDefinition import: pull the plugin endpoint page-by-page (via a
 * {@link NativeGroupDefinitionPageSource}), parse each page, upsert every record (via a
 * {@link NativeGroupDefinitionSink}). Pure orchestration — no HTTP and no JDBC — unit-testable with
 * fakes. Mirrors the validated native imports: confirmed-full-scan deletion sweep with empty-source
 * guard. Tracks GROUP vs POPULATION counts for the extraction summary.
 */
public final class NativeGroupDefinitionImportService {

    public static final class Result {
        private final int extracted;
        private final int groups;
        private final int populations;
        private final int inserted;
        private final int updated;
        private final int failed;
        private final int marked;
        private final int revived;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int extracted, int groups, int populations, int inserted, int updated, int failed,
               int marked, int revived, boolean sweepRan, boolean sweepSkipped, List<String> failures) {
            this.extracted = extracted;
            this.groups = groups;
            this.populations = populations;
            this.inserted = inserted;
            this.updated = updated;
            this.failed = failed;
            this.marked = marked;
            this.revived = revived;
            this.sweepRan = sweepRan;
            this.sweepSkipped = sweepSkipped;
            this.failures = failures;
        }

        public int getProcessed() { return extracted; }
        public int getExtracted() { return extracted; }
        public int getGroups() { return groups; }
        public int getPopulations() { return populations; }
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

    private final NativeGroupDefinitionPageSource source;
    private final NativeGroupDefinitionParser parser = new NativeGroupDefinitionParser();
    private final NativeGroupDefinitionSink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeGroupDefinitionImportService(NativeGroupDefinitionPageSource source,
                                              NativeGroupDefinitionSink sink,
                                              int pageSize, boolean sweepDeletions) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 100;
        this.sweepDeletions = sweepDeletions;
    }

    public Result importAll() throws SQLException {
        sink.ensure();

        int extracted = 0;
        int groups = 0;
        int populations = 0;
        int inserted = 0;
        int updated = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        Set<String> keep = new LinkedHashSet<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            List<NativeGroupDefinitionRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeGroupDefinitionRecord rec : page) {
                extracted++;
                if ("GROUP".equals(rec.type)) {
                    groups++;
                } else if ("POPULATION".equals(rec.type)) {
                    populations++;
                }
                keep.add(NativeGroupDefinitionRepository.canonicalGroupId(rec));
                try {
                    NativeGroupDefinitionRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativeGroupDefinitionRepository.UpsertOutcome.INSERTED) {
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

        return new Result(extracted, groups, populations, inserted, updated, failed,
                marked, revived, sweepRan, sweepSkipped, failures);
    }
}
