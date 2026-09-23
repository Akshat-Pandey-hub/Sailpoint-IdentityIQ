package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native CertificationEntity import (current-state): page → parse → upsert → confirmed-full-scan
 * sweep. The authoritative {@code countObjects} total is verified against the extracted count; an incomplete
 * scan throws before sweeping so a partial pull never marks live rows deleted.
 */
public final class NativeCertificationEntityImportService {

    public static final class Result {
        private final int extracted;
        private final int inserted;
        private final int updated;
        private final int failed;
        private final int marked;
        private final int revived;
        private final int sourceCount;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int extracted, int inserted, int updated, int failed, int marked, int revived,
               int sourceCount, boolean sweepRan, boolean sweepSkipped, List<String> failures) {
            this.extracted = extracted;
            this.inserted = inserted;
            this.updated = updated;
            this.failed = failed;
            this.marked = marked;
            this.revived = revived;
            this.sourceCount = sourceCount;
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
        public int getSourceCount() { return sourceCount; }
        public boolean isSweepRan() { return sweepRan; }
        public boolean isSweepSkipped() { return sweepSkipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeCertificationEntityPageSource source;
    private final NativeCertificationEntityParser parser = new NativeCertificationEntityParser();
    private final NativeCertificationEntitySink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeCertificationEntityImportService(NativeCertificationEntityPageSource source,
                                                  NativeCertificationEntitySink sink,
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
        int sourceCount = -1;
        List<String> failures = new ArrayList<>();
        Set<String> keep = new LinkedHashSet<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSourceCount = parser.sourceCount(json);
            if (pageSourceCount >= 0) {
                sourceCount = pageSourceCount;
            }
            List<NativeCertificationEntityRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeCertificationEntityRecord rec : page) {
                extracted++;
                keep.add(NativeCertificationEntityRepository.canonicalCertificationEntityId(rec));
                try {
                    NativeCertificationEntityRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativeCertificationEntityRepository.UpsertOutcome.INSERTED) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.identity != null ? rec.identity : rec.sourceId) + ": " + e.getMessage());
                    }
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        if (sourceCount >= 0 && extracted != sourceCount) {
            throw new NativeImportException("Incomplete CertificationEntity scan: sourceCount=" + sourceCount
                    + ", extracted=" + extracted + ". No deletion action was taken.");
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

        return new Result(extracted, inserted, updated, failed, marked, revived,
                sourceCount, sweepRan, sweepSkipped, failures);
    }
}
