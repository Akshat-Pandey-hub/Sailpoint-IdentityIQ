package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native IdentityEntitlement import: pull the plugin endpoint page-by-page (via a
 * {@link NativeIdentityEntitlementPageSource}), parse each page, upsert every record (via a
 * {@link NativeIdentityEntitlementSink}), then run the confirmed-full-scan deletion sweep. Pure
 * orchestration — no HTTP and no JDBC — unit-testable with fakes.
 *
 * <p><b>Safety:</b> the authoritative {@code sourceCount} (from IIQ {@code countObjects}) is verified
 * against the number actually extracted. On an incomplete scan the service throws <b>before</b> sweeping,
 * so a partial pull can never mark live rows deleted. The sweep also carries the shared empty-source guard.
 */
public final class NativeIdentityEntitlementImportService {

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

    private final NativeIdentityEntitlementPageSource source;
    private final NativeIdentityEntitlementParser parser = new NativeIdentityEntitlementParser();
    private final NativeIdentityEntitlementSink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeIdentityEntitlementImportService(NativeIdentityEntitlementPageSource source,
                                                  NativeIdentityEntitlementSink sink,
                                                  int pageSize, boolean sweepDeletions) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 500;
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
            List<NativeIdentityEntitlementRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeIdentityEntitlementRecord rec : page) {
                extracted++;
                keep.add(NativeIdentityEntitlementRepository.canonicalEntitlementId(rec));
                try {
                    NativeIdentityEntitlementRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativeIdentityEntitlementRepository.UpsertOutcome.INSERTED) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.identityName != null ? rec.identityName : rec.sourceId) + ": " + e.getMessage());
                    }
                }
            }
            if (page.size() < pageSize) {
                break; // scan complete
            }
            start += pageSize;
        }

        // Incomplete-scan guard: never sweep on a short/partial pull (would wrongly mark rows deleted).
        if (sourceCount >= 0 && extracted != sourceCount) {
            throw new NativeImportException("Incomplete IdentityEntitlement scan: sourceCount=" + sourceCount
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
