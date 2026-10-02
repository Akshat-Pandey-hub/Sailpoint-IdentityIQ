package com.keyforge.nativeload;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the native SyslogEvent import: page the plugin endpoint, parse, and APPEND every record.
 * Append-only — no deletion sweep (syslog events are immutable). A confirmed-complete-scan guard throws
 * if fewer rows were seen than the source {@code countObjects}. Unit-testable with fakes.
 */
public final class NativeSyslogEventImportService {

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
        public int getPersisted() { return inserted + skipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeSyslogEventPageSource source;
    private final NativeSyslogEventParser parser = new NativeSyslogEventParser();
    private final NativeSyslogEventSink sink;
    private final int pageSize;

    public NativeSyslogEventImportService(NativeSyslogEventPageSource source, NativeSyslogEventSink sink, int pageSize) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 200;
    }

    public Result importAll() throws SQLException {
        sink.ensure();

        int extracted = 0;
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        int sourceCount = -1;
        List<String> failures = new ArrayList<>();

        int maxSourceCount = -1; // high-water source count observed across pages
        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSourceCount = parser.sourceCount(json);
            if (pageSourceCount >= 0) {
                if (sourceCount < 0) {
                    sourceCount = pageSourceCount; // baseline the scan commits to covering
                }
                // SyslogEvent is append-only: the source count only GROWS as new events land at the tail
                // during a multi-page scan. Growth is benign — the new tail events are captured later in
                // this scan or on the next run, and the idempotent append (dedup on event id) absorbs any
                // overlap, so no gap forms. A DECREASE means events were removed mid-scan (rotation/purge),
                // which can shift the paging window and silently drop rows — that stays fatal.
                if (maxSourceCount >= 0 && pageSourceCount < maxSourceCount) {
                    throw new NativeImportException("SyslogEvent source count shrank during paginated extraction: "
                            + maxSourceCount + " -> " + pageSourceCount
                            + " (events removed mid-scan; aborting to avoid a gap)");
                }
                maxSourceCount = Math.max(maxSourceCount, pageSourceCount);
            }
            List<NativeSyslogEventRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeSyslogEventRecord rec : page) {
                extracted++;
                try {
                    NativeSyslogEventRepository.AppendOutcome outcome = sink.append(rec);
                    if (outcome == NativeSyslogEventRepository.AppendOutcome.INSERTED) {
                        inserted++;
                    } else {
                        skipped++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.sourceId != null ? rec.sourceId : rec.eventLevel) + ": " + e.getMessage());
                    }
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        // Complete iff we extracted at least the baseline the source had when the scan started. Extra
        // tail events appended during the scan (extracted > baseline) are expected for append-only data.
        if (sourceCount >= 0 && extracted < sourceCount) {
            throw new NativeImportException("Incomplete SyslogEvent scan: baseline sourceCount=" + sourceCount
                    + ", extracted=" + extracted);
        }
        return new Result(extracted, inserted, skipped, failed, sourceCount, failures);
    }
}
