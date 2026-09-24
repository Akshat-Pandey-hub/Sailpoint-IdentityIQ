package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native identity-role import. Rows are per-edge but the paginated source unit is the
 * {@code Identity}, so this pages by identity: it advances until a page covers fewer identities than the
 * page size ({@code returnedIdentities < pageSize}), never on empty edges (an identity with no roles yields
 * zero edges but must not stop the scan). The authoritative total Identity count is compared to the number
 * scanned; an incomplete scan throws <b>before</b> sweeping so a partial pull never marks live edges deleted.
 */
public final class NativeIdentityRoleImportService {

    public static final class Result {
        private final int extracted;
        private final int inserted;
        private final int updated;
        private final int failed;
        private final int marked;
        private final int revived;
        private final int sourceIdentityCount;
        private final int scannedIdentities;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int extracted, int inserted, int updated, int failed, int marked, int revived,
               int sourceIdentityCount, int scannedIdentities, boolean sweepRan, boolean sweepSkipped,
               List<String> failures) {
            this.extracted = extracted;
            this.inserted = inserted;
            this.updated = updated;
            this.failed = failed;
            this.marked = marked;
            this.revived = revived;
            this.sourceIdentityCount = sourceIdentityCount;
            this.scannedIdentities = scannedIdentities;
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
        public int getSourceIdentityCount() { return sourceIdentityCount; }
        public int getScannedIdentities() { return scannedIdentities; }
        public boolean isSweepRan() { return sweepRan; }
        public boolean isSweepSkipped() { return sweepSkipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeIdentityRolePageSource source;
    private final NativeIdentityRoleParser parser = new NativeIdentityRoleParser();
    private final NativeIdentityRoleSink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeIdentityRoleImportService(NativeIdentityRolePageSource source, NativeIdentityRoleSink sink,
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
        int sourceIdentityCount = -1;
        int scannedIdentities = 0;
        List<String> failures = new ArrayList<>();
        Set<String> keep = new LinkedHashSet<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSource = parser.sourceCount(json);
            if (pageSource >= 0) {
                sourceIdentityCount = pageSource;
            }
            int returnedIdentities = parser.returnedIdentities(json);
            List<NativeIdentityRoleRecord> page = parser.parse(json);

            for (NativeIdentityRoleRecord rec : page) {
                extracted++;
                keep.add(NativeIdentityRoleRepository.canonicalEdgeId(rec));
                try {
                    NativeIdentityRoleRepository.UpsertOutcome outcome = sink.upsert(rec);
                    if (outcome == NativeIdentityRoleRepository.UpsertOutcome.INSERTED) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.identityId != null ? rec.identityId : "?") + "/" + rec.roleName
                                + ": " + e.getClass().getName());
                    }
                }
            }

            if (returnedIdentities >= 0) {
                scannedIdentities += returnedIdentities;
                if (returnedIdentities < pageSize) {
                    break;
                }
            } else if (page.isEmpty()) {
                break;
            }
            start += pageSize;
        }

        if (sourceIdentityCount >= 0 && scannedIdentities != sourceIdentityCount) {
            throw new NativeImportException("Incomplete identity-role scan: source Identities=" + sourceIdentityCount
                    + ", scanned=" + scannedIdentities + ". No deletion action was taken.");
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
                sourceIdentityCount, scannedIdentities, sweepRan, sweepSkipped, failures);
    }
}
