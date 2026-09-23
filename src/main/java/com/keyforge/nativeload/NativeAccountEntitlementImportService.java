package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native account-entitlement import. Because rows are per-value edges but the paginated
 * source unit is the {@code Link}, this service pages by <b>Link</b>: it advances the window until a page
 * covers fewer Links than the page size ({@code returnedLinks < pageSize}), never on empty edges (a Link
 * with no entitlements yields zero edges but must not stop the scan). Pure orchestration, unit-testable
 * with fakes.
 *
 * <p><b>Safety:</b> the authoritative total Link count ({@code countObjects(Link.class)}) is compared to
 * the number of Links actually scanned. On an incomplete scan the service throws <b>before</b> sweeping, so
 * a partial pull can never mark live edges deleted. The sweep also carries the shared empty-source guard.
 */
public final class NativeAccountEntitlementImportService {

    public static final class Result {
        private final int extracted;
        private final int inserted;
        private final int updated;
        private final int failed;
        private final int marked;
        private final int revived;
        private final int sourceLinkCount;
        private final int scannedLinks;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int extracted, int inserted, int updated, int failed, int marked, int revived,
               int sourceLinkCount, int scannedLinks, boolean sweepRan, boolean sweepSkipped,
               List<String> failures) {
            this.extracted = extracted;
            this.inserted = inserted;
            this.updated = updated;
            this.failed = failed;
            this.marked = marked;
            this.revived = revived;
            this.sourceLinkCount = sourceLinkCount;
            this.scannedLinks = scannedLinks;
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
        public int getSourceLinkCount() { return sourceLinkCount; }
        public int getScannedLinks() { return scannedLinks; }
        public boolean isSweepRan() { return sweepRan; }
        public boolean isSweepSkipped() { return sweepSkipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeAccountEntitlementPageSource source;
    private final NativeAccountEntitlementParser parser = new NativeAccountEntitlementParser();
    private final NativeAccountEntitlementSink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeAccountEntitlementImportService(NativeAccountEntitlementPageSource source,
                                                 NativeAccountEntitlementSink sink,
                                                 int pageSize, boolean sweepDeletions) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 200;
        this.sweepDeletions = sweepDeletions;
    }

    public Result importAll() throws SQLException {
        sink.ensure();

        int extracted = 0;
        int inserted = 0;
        int updated = 0;
        int failed = 0;
        int sourceLinkCount = -1;
        int scannedLinks = 0;
        List<String> failures = new ArrayList<>();
        Set<String> keep = new LinkedHashSet<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSourceCount = parser.sourceCount(json);
            if (pageSourceCount >= 0) {
                sourceLinkCount = pageSourceCount;
            }
            int returnedLinks = parser.returnedLinks(json);
            List<NativeAccountEntitlementRecord> page = parser.parse(json);

            for (NativeAccountEntitlementRecord rec : page) {
                extracted++;
                keep.add(NativeAccountEntitlementRepository.canonicalEdgeId(rec));
                try {
                    NativeAccountEntitlementRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativeAccountEntitlementRepository.UpsertOutcome.INSERTED) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.identityName != null ? rec.identityName : rec.linkId)
                                + "/" + rec.attributeName + ": " + e.getMessage());
                    }
                }
            }

            if (returnedLinks >= 0) {
                scannedLinks += returnedLinks;
                if (returnedLinks < pageSize) {
                    break; // fewer Links than the window ⇒ scan complete
                }
            } else if (page.isEmpty()) {
                break; // fallback termination when the endpoint does not report link counts
            }
            start += pageSize;
        }

        // Incomplete-scan guard: never sweep unless every Link was scanned.
        if (sourceLinkCount >= 0 && scannedLinks != sourceLinkCount) {
            throw new NativeImportException("Incomplete account-entitlement scan: source Links=" + sourceLinkCount
                    + ", scanned Links=" + scannedLinks + ". No deletion action was taken.");
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
                sourceLinkCount, scannedLinks, sweepRan, sweepSkipped, failures);
    }
}
