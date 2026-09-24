package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the native IdentityRequest import (current-state): page → parse → upsert each request and its
 * nested items + approval summaries → confirmed-full-scan soft-delete sweep of all three tables. The
 * authoritative {@code sourceCount} request total is verified against the extracted request count; an
 * incomplete scan throws <b>before</b> sweeping so a partial pull never marks live rows deleted. A request
 * whose upsert fails is isolated (its children are skipped and it is dropped from the keep-set). Failure
 * messages carry only the exception class name (never source detail) so nothing secret-like can leak.
 */
public final class NativeIdentityRequestImportService {

    public static final class Result {
        private final int requests;
        private final int requestInserted;
        private final int requestUpdated;
        private final int items;
        private final int itemInserted;
        private final int itemUpdated;
        private final int approvals;
        private final int approvalInserted;
        private final int approvalUpdated;
        private final int failed;
        private final int sourceCount;
        private final int requestMarked;
        private final int itemMarked;
        private final int approvalMarked;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int requests, int requestInserted, int requestUpdated, int items, int itemInserted, int itemUpdated,
               int approvals, int approvalInserted, int approvalUpdated, int failed, int sourceCount,
               int requestMarked, int itemMarked, int approvalMarked, boolean sweepRan, boolean sweepSkipped,
               List<String> failures) {
            this.requests = requests;
            this.requestInserted = requestInserted;
            this.requestUpdated = requestUpdated;
            this.items = items;
            this.itemInserted = itemInserted;
            this.itemUpdated = itemUpdated;
            this.approvals = approvals;
            this.approvalInserted = approvalInserted;
            this.approvalUpdated = approvalUpdated;
            this.failed = failed;
            this.sourceCount = sourceCount;
            this.requestMarked = requestMarked;
            this.itemMarked = itemMarked;
            this.approvalMarked = approvalMarked;
            this.sweepRan = sweepRan;
            this.sweepSkipped = sweepSkipped;
            this.failures = failures;
        }

        public int getRequests() { return requests; }
        public int getRequestInserted() { return requestInserted; }
        public int getRequestUpdated() { return requestUpdated; }
        public int getRequestPersisted() { return requestInserted + requestUpdated; }
        public int getItems() { return items; }
        public int getItemInserted() { return itemInserted; }
        public int getItemUpdated() { return itemUpdated; }
        public int getItemPersisted() { return itemInserted + itemUpdated; }
        public int getApprovals() { return approvals; }
        public int getApprovalInserted() { return approvalInserted; }
        public int getApprovalUpdated() { return approvalUpdated; }
        public int getApprovalPersisted() { return approvalInserted + approvalUpdated; }
        public int getFailed() { return failed; }
        public int getSourceCount() { return sourceCount; }
        public int getRequestMarkedDeleted() { return requestMarked; }
        public int getItemMarkedDeleted() { return itemMarked; }
        public int getApprovalMarkedDeleted() { return approvalMarked; }
        public boolean isSweepRan() { return sweepRan; }
        public boolean isSweepSkipped() { return sweepSkipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeIdentityRequestPageSource source;
    private final NativeIdentityRequestParser parser = new NativeIdentityRequestParser();
    private final NativeIdentityRequestSink sink;
    private final int pageSize;
    private final boolean sweepDeletions;

    public NativeIdentityRequestImportService(NativeIdentityRequestPageSource source,
                                              NativeIdentityRequestSink sink,
                                              int pageSize, boolean sweepDeletions) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 100;
        this.sweepDeletions = sweepDeletions;
    }

    public Result importAll() throws SQLException {
        sink.ensure();

        int requests = 0;
        int requestInserted = 0;
        int requestUpdated = 0;
        int items = 0;
        int itemInserted = 0;
        int itemUpdated = 0;
        int approvals = 0;
        int approvalInserted = 0;
        int approvalUpdated = 0;
        int failed = 0;
        int sourceCount = -1;
        List<String> failures = new ArrayList<>();
        Set<String> keepRequests = new LinkedHashSet<>();
        Set<String> keepItems = new LinkedHashSet<>();
        Set<String> keepApprovals = new LinkedHashSet<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSourceCount = parser.sourceCount(json);
            if (pageSourceCount >= 0) {
                sourceCount = pageSourceCount;
            }
            List<NativeIdentityRequestRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeIdentityRequestRecord rec : page) {
                requests++;
                keepRequests.add(NativeIdentityRequestRepository.canonicalRequestId(rec));
                try {
                    NativeIdentityRequestRepository.UpsertOutcome outcome = sink.upsertRequest(rec);
                    if (outcome == NativeIdentityRequestRepository.UpsertOutcome.INSERTED) {
                        requestInserted++;
                    } else {
                        requestUpdated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add("request " + rec.sourceId + ": " + e.getClass().getName());
                    }
                    keepRequests.remove(NativeIdentityRequestRepository.canonicalRequestId(rec));
                    continue; // request failed to persist: skip its children and drop it from the keep-set
                }
                for (NativeIdentityRequestItemRecord item : rec.items) {
                    items++;
                    keepItems.add(NativeIdentityRequestItemRepository.canonicalItemId(item));
                    try {
                        NativeIdentityRequestItemRepository.UpsertOutcome io = sink.upsertItem(item);
                        if (io == NativeIdentityRequestItemRepository.UpsertOutcome.INSERTED) {
                            itemInserted++;
                        } else {
                            itemUpdated++;
                        }
                    } catch (SQLException | RuntimeException e) {
                        failed++;
                        if (failures.size() < 50) {
                            failures.add("item under request " + item.requestSourceId + ": " + e.getClass().getName());
                        }
                    }
                }
                for (NativeIdentityRequestApprovalRecord approval : rec.approvals) {
                    approvals++;
                    keepApprovals.add(NativeIdentityRequestApprovalRepository.canonicalApprovalId(approval));
                    try {
                        NativeIdentityRequestApprovalRepository.UpsertOutcome ao = sink.upsertApproval(approval);
                        if (ao == NativeIdentityRequestApprovalRepository.UpsertOutcome.INSERTED) {
                            approvalInserted++;
                        } else {
                            approvalUpdated++;
                        }
                    } catch (SQLException | RuntimeException e) {
                        failed++;
                        if (failures.size() < 50) {
                            failures.add("approval under request " + approval.requestSourceId + " at index "
                                    + approval.approvalIndex + ": " + e.getClass().getName());
                        }
                    }
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        // Incomplete-scan guard: never sweep on a short/partial pull (would wrongly mark rows deleted).
        if (sourceCount >= 0 && requests != sourceCount) {
            throw new NativeImportException("Incomplete IdentityRequest scan: sourceCount=" + sourceCount
                    + ", extracted=" + requests + ". No deletion action was taken.");
        }

        int requestMarked = 0;
        int itemMarked = 0;
        int approvalMarked = 0;
        boolean sweepRan = false;
        boolean sweepSkipped = false;
        if (sweepDeletions) {
            SoftDeleteSweeper.SweepResult requestSweep = sink.sweepRequests(keepRequests);
            SoftDeleteSweeper.SweepResult itemSweep = sink.sweepItems(keepItems);
            SoftDeleteSweeper.SweepResult approvalSweep = sink.sweepApprovals(keepApprovals);
            sweepRan = true;
            sweepSkipped = requestSweep.skipped();
            requestMarked = requestSweep.marked();
            itemMarked = itemSweep.marked();
            approvalMarked = approvalSweep.marked();
        }

        return new Result(requests, requestInserted, requestUpdated, items, itemInserted, itemUpdated,
                approvals, approvalInserted, approvalUpdated, failed, sourceCount, requestMarked, itemMarked,
                approvalMarked, sweepRan, sweepSkipped, failures);
    }
}
