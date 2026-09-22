package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native Identity import: pull the plugin endpoint page-by-page (via a
 * {@link NativeIdentityPageSource}), parse each page, and upsert every record into the native store
 * (via a {@link NativeIdentitySink}). Pure orchestration — no HTTP and no JDBC here — so it is
 * unit-testable with fakes and no live server.
 *
 * <p>Read-only against IIQ (the plugin does the reading); the only writes are to our own PostgreSQL,
 * and every write is an idempotent upsert, so re-running updates rows rather than duplicating them.
 *
 * <p>Current-state semantics: when {@code sweepDeletions} is enabled, a soft-delete sweep runs
 * <b>only after a confirmed complete scan</b> (the paging loop ran to exhaustion without error), over
 * the authoritative keep-set of every source id seen. A mid-scan failure propagates and the sweep is
 * skipped, so a partial/failed fetch can never mark rows deleted; the {@link SoftDeleteSweeper}'s own
 * empty-source guard is a second line of defence.
 */
public final class NativeIdentityImportService {

    /** Extraction summary: processed/extracted/persisted/failed plus the deletion-sweep outcome. */
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

        public int getProcessed() { return extracted; }
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

    private final NativeIdentityPageSource source;
    private final NativeIdentityParser parser = new NativeIdentityParser();
    private final NativeIdentitySink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeIdentityImportService(NativeIdentityPageSource source, NativeIdentitySink sink,
                                       int pageSize, boolean sweepDeletions) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 100;
        this.sweepDeletions = sweepDeletions;
    }

    /**
     * Full extraction: pull every page, upsert every record, then (if enabled) sweep deletions.
     * A page shorter than {@code pageSize} (or empty) ends paging. Row-level failures are isolated and
     * counted so one bad record never aborts the run.
     */
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
            List<NativeIdentityRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeIdentityRecord rec : page) {
                extracted++;
                // Authoritative current-source keep-set: every id the source returned, whether or not
                // this run's upsert succeeded (a row that failed to update still exists and must not be
                // swept as deleted).
                keep.add(NativeIdentityRepository.canonicalUserid(rec));
                try {
                    NativeIdentityRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativeIdentityRepository.UpsertOutcome.INSERTED) {
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
                break; // last (partial) page — scan complete
            }
            start += pageSize;
        }

        // The scan completed without a fetch error (any fetch failure would have propagated above).
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
