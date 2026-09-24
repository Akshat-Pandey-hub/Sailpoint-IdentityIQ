package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native policy-constraint import. Rows are per-constraint but the paginated source unit
 * is the parent {@code Policy}, so this pages by policy: it advances until a page covers fewer policies
 * than the page size ({@code returnedPolicies < pageSize}), never on empty constraints (a policy with no
 * constraints yields zero rows but must not stop the scan). The authoritative total Policy count is
 * compared to the number scanned; an incomplete scan throws <b>before</b> sweeping.
 */
public final class NativePolicyConstraintImportService {

    public static final class Result {
        private final int extracted;
        private final int inserted;
        private final int updated;
        private final int failed;
        private final int marked;
        private final int revived;
        private final int sourcePolicyCount;
        private final int scannedPolicies;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int extracted, int inserted, int updated, int failed, int marked, int revived,
               int sourcePolicyCount, int scannedPolicies, boolean sweepRan, boolean sweepSkipped,
               List<String> failures) {
            this.extracted = extracted;
            this.inserted = inserted;
            this.updated = updated;
            this.failed = failed;
            this.marked = marked;
            this.revived = revived;
            this.sourcePolicyCount = sourcePolicyCount;
            this.scannedPolicies = scannedPolicies;
            this.sweepRan = sweepRan;
            this.sweepSkipped = sweepSkipped;
            this.failures = failures;
        }

        public int getExtracted() { return extracted; }
        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getPersisted() { return inserted + updated; }
        public int getFailed() { return failed; }
        public int getMarkedDeleted() { return marked; }
        public int getRevived() { return revived; }
        public int getSourcePolicyCount() { return sourcePolicyCount; }
        public int getScannedPolicies() { return scannedPolicies; }
        public boolean isSweepRan() { return sweepRan; }
        public boolean isSweepSkipped() { return sweepSkipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativePolicyConstraintPageSource source;
    private final NativePolicyConstraintParser parser = new NativePolicyConstraintParser();
    private final NativePolicyConstraintSink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativePolicyConstraintImportService(NativePolicyConstraintPageSource source,
                                               NativePolicyConstraintSink sink, int pageSize, boolean sweepDeletions) {
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
        int sourcePolicyCount = -1;
        int scannedPolicies = 0;
        List<String> failures = new ArrayList<>();
        Set<String> keep = new LinkedHashSet<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSource = parser.sourceCount(json);
            if (pageSource >= 0) {
                sourcePolicyCount = pageSource;
            }
            int returnedPolicies = parser.returnedPolicies(json);
            List<NativePolicyConstraintRecord> page = parser.parse(json);

            for (NativePolicyConstraintRecord rec : page) {
                extracted++;
                keep.add(NativePolicyConstraintRepository.canonicalConstraintId(rec));
                try {
                    NativePolicyConstraintRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativePolicyConstraintRepository.UpsertOutcome.INSERTED) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.policyName != null ? rec.policyName : rec.policyId) + "/" + rec.name
                                + ": " + e.getClass().getName());
                    }
                }
            }

            if (returnedPolicies >= 0) {
                scannedPolicies += returnedPolicies;
                if (returnedPolicies < pageSize) {
                    break;
                }
            } else if (page.isEmpty()) {
                break;
            }
            start += pageSize;
        }

        if (sourcePolicyCount >= 0 && scannedPolicies != sourcePolicyCount) {
            throw new NativeImportException("Incomplete policy-constraint scan: source Policies=" + sourcePolicyCount
                    + ", scanned=" + scannedPolicies + ". No deletion action was taken.");
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
                sourcePolicyCount, scannedPolicies, sweepRan, sweepSkipped, failures);
    }
}
