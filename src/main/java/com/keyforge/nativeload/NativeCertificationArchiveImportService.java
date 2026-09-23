package com.keyforge.nativeload;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the native CertificationArchive import: pull the plugin endpoint page-by-page (via a
 * {@link NativeCertificationArchivePageSource}), parse each page, append every record (via a
 * {@link NativeCertificationArchiveSink}). Pure orchestration — no HTTP and no JDBC — unit-testable with fakes.
 *
 * <p><b>Append-only CEC import.</b> Unlike the current-state native imports there is <b>no deletion sweep</b>:
 * an archive is an immutable historical record, so nothing is ever expired. Re-running is safe and
 * idempotent — the deterministic {@code certificationarchiveid} means an already-present archive is counted
 * as {@code skipped} (deduped), never duplicated or rewritten.
 */
public final class NativeCertificationArchiveImportService {

    public static final class Result {
        private final int extracted;
        private final int inserted;
        private final int skipped;
        private final int failed;
        private final int sourceCount;
        private final List<String> failures;

        Result(int extracted, int inserted, int skipped, int failed, int sourceCount, List<String> failures) {
            this.extracted = extracted;
            this.inserted = inserted;
            this.skipped = skipped;
            this.failed = failed;
            this.sourceCount = sourceCount;
            this.failures = failures;
        }

        public int getExtracted() { return extracted; }
        public int getInserted() { return inserted; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        public int getSourceCount() { return sourceCount; }
        /** Rows successfully accounted for (newly appended + already-present deduped). */
        public int getPersisted() { return inserted + skipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeCertificationArchivePageSource source;
    private final NativeCertificationArchiveParser parser = new NativeCertificationArchiveParser();
    private final NativeCertificationArchiveSink sink;
    private final int pageSize;

    public NativeCertificationArchiveImportService(NativeCertificationArchivePageSource source,
                                                   NativeCertificationArchiveSink sink, int pageSize) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 100;
    }

    public Result importAll() throws SQLException {
        sink.ensure();

        int extracted = 0;
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        int sourceCount = -1;
        List<String> failures = new ArrayList<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSourceCount = parser.sourceCount(json);
            if (pageSourceCount >= 0) {
                if (sourceCount >= 0 && sourceCount != pageSourceCount) {
                    throw new NativeImportException("CertificationArchive source count changed during paginated extraction: "
                            + sourceCount + " -> " + pageSourceCount);
                }
                sourceCount = pageSourceCount;
            }
            List<NativeCertificationArchiveRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeCertificationArchiveRecord rec : page) {
                extracted++;
                try {
                    NativeCertificationArchiveRepository.AppendOutcome outcome = sink.append(rec);
                    if (outcome == NativeCertificationArchiveRepository.AppendOutcome.INSERTED) {
                        inserted++;
                    } else {
                        skipped++;
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

        if (sourceCount >= 0 && extracted != sourceCount) {
            throw new NativeImportException("Incomplete CertificationArchive scan: sourceCount=" + sourceCount
                    + ", extracted=" + extracted + ". No deletion action was taken.");
        }
        return new Result(extracted, inserted, skipped, failed, sourceCount, failures);
    }
}
