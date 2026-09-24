package com.keyforge.nativeload;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the native AuditEvent import: page the plugin endpoint, parse, and APPEND every record
 * (via a {@link NativeAuditEventSink}). Append-only — there is <b>no deletion sweep</b> (audit events are
 * immutable historical evidence). A confirmed-complete-scan guard throws if fewer rows were seen than the
 * source {@code countObjects}, so a partial pull is never reported as complete. Unit-testable with fakes.
 */
public final class NativeAuditEventImportService {

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

    private final NativeAuditEventPageSource source;
    private final NativeAuditEventParser parser = new NativeAuditEventParser();
    private final NativeAuditEventSink sink;
    private final int pageSize;

    public NativeAuditEventImportService(NativeAuditEventPageSource source, NativeAuditEventSink sink, int pageSize) {
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

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSourceCount = parser.sourceCount(json);
            if (pageSourceCount >= 0) {
                if (sourceCount >= 0 && sourceCount != pageSourceCount) {
                    throw new NativeImportException("AuditEvent source count changed during paginated extraction: "
                            + sourceCount + " -> " + pageSourceCount);
                }
                sourceCount = pageSourceCount;
            }
            List<NativeAuditEventRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeAuditEventRecord rec : page) {
                extracted++;
                try {
                    NativeAuditEventRepository.AppendOutcome outcome = sink.append(rec);
                    if (outcome == NativeAuditEventRepository.AppendOutcome.INSERTED) {
                        inserted++;
                    } else {
                        skipped++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add((rec.sourceId != null ? rec.sourceId : rec.action) + ": " + e.getMessage());
                    }
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        if (sourceCount >= 0 && extracted != sourceCount) {
            throw new NativeImportException("Incomplete AuditEvent scan: sourceCount=" + sourceCount
                    + ", extracted=" + extracted);
        }
        return new Result(extracted, inserted, skipped, failed, sourceCount, failures);
    }
}
