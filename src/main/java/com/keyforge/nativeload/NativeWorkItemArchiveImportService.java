package com.keyforge.nativeload;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the native WorkItemArchive import: pull the plugin endpoint page-by-page (via a
 * {@link NativeWorkItemArchivePageSource}), parse each page, append every record (via a
 * {@link NativeWorkItemArchiveSink}). Pure orchestration — no HTTP and no JDBC — unit-testable with fakes.
 *
 * <p><b>Append-only CEC import.</b> Unlike the current-state native imports there is <b>no deletion sweep</b>:
 * an archive is an immutable historical record, so nothing is ever expired. Re-running is safe and
 * idempotent — the deterministic {@code archiveid} means an already-present archive is counted as
 * {@code skipped} (deduped), never duplicated or rewritten.
 */
public final class NativeWorkItemArchiveImportService {

    public static final class Result {
        private final int extracted;
        private final int inserted;
        private final int skipped;
        private final int failed;
        private final List<String> failures;

        Result(int extracted, int inserted, int skipped, int failed, List<String> failures) {
            this.extracted = extracted;
            this.inserted = inserted;
            this.skipped = skipped;
            this.failed = failed;
            this.failures = failures;
        }

        public int getExtracted() { return extracted; }
        public int getInserted() { return inserted; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        /** Rows successfully accounted for (newly appended + already-present deduped). */
        public int getPersisted() { return inserted + skipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeWorkItemArchivePageSource source;
    private final NativeWorkItemArchiveParser parser = new NativeWorkItemArchiveParser();
    private final NativeWorkItemArchiveSink sink;
    private final int pageSize;

    public NativeWorkItemArchiveImportService(NativeWorkItemArchivePageSource source,
                                              NativeWorkItemArchiveSink sink, int pageSize) {
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
        List<String> failures = new ArrayList<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            List<NativeWorkItemArchiveRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeWorkItemArchiveRecord rec : page) {
                extracted++;
                try {
                    NativeWorkItemArchiveRepository.AppendOutcome outcome = sink.append(rec);
                    if (outcome == NativeWorkItemArchiveRepository.AppendOutcome.INSERTED) {
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

        return new Result(extracted, inserted, skipped, failed, failures);
    }
}
