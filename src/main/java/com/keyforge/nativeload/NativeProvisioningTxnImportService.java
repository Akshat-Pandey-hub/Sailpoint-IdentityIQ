package com.keyforge.nativeload;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the native ProvisioningTransaction import: page, parse, and upsert each transaction and its
 * derived items. Because the 8.4 API does not establish that missing transactions mean deletion, this import
 * deliberately never sweeps either table. It verifies the advertised source count when available and keeps
 * historical rows if they disappear from a later response.
 */
public final class NativeProvisioningTxnImportService {

    public static final class Result {
        private final int txns;
        private final int txnInserted;
        private final int txnUpdated;
        private final int items;
        private final int itemInserted;
        private final int itemUpdated;
        private final int failed;
        private final int sourceCount;
        private final int txnMarked;
        private final int itemMarked;
        private final boolean sweepRan;
        private final boolean sweepSkipped;
        private final List<String> failures;

        Result(int txns, int txnInserted, int txnUpdated, int items, int itemInserted, int itemUpdated,
               int failed, int sourceCount, int txnMarked, int itemMarked, boolean sweepRan,
               boolean sweepSkipped, List<String> failures) {
            this.txns = txns;
            this.txnInserted = txnInserted;
            this.txnUpdated = txnUpdated;
            this.items = items;
            this.itemInserted = itemInserted;
            this.itemUpdated = itemUpdated;
            this.failed = failed;
            this.sourceCount = sourceCount;
            this.txnMarked = txnMarked;
            this.itemMarked = itemMarked;
            this.sweepRan = sweepRan;
            this.sweepSkipped = sweepSkipped;
            this.failures = failures;
        }

        public int getTxns() { return txns; }
        public int getTxnInserted() { return txnInserted; }
        public int getTxnUpdated() { return txnUpdated; }
        public int getTxnPersisted() { return txnInserted + txnUpdated; }
        public int getItems() { return items; }
        public int getItemInserted() { return itemInserted; }
        public int getItemUpdated() { return itemUpdated; }
        public int getItemPersisted() { return itemInserted + itemUpdated; }
        public int getFailed() { return failed; }
        public int getSourceCount() { return sourceCount; }
        public int getTxnMarkedDeleted() { return txnMarked; }
        public int getItemMarkedDeleted() { return itemMarked; }
        public boolean isSweepRan() { return sweepRan; }
        public boolean isSweepSkipped() { return sweepSkipped; }
        public List<String> getFailures() { return failures; }
    }

    private final NativeProvisioningTxnPageSource source;
    private final NativeProvisioningTxnParser parser = new NativeProvisioningTxnParser();
    private final NativeProvisioningTxnSink sink;
    private final int pageSize;

    public NativeProvisioningTxnImportService(NativeProvisioningTxnPageSource source,
                                              NativeProvisioningTxnSink sink, int pageSize) {
        this.source = source;
        this.sink = sink;
        this.pageSize = pageSize > 0 ? pageSize : 100;
    }

    public Result importAll() throws SQLException {
        sink.ensure();

        int txns = 0;
        int txnInserted = 0;
        int txnUpdated = 0;
        int items = 0;
        int itemInserted = 0;
        int itemUpdated = 0;
        int failed = 0;
        int sourceCount = -1;
        List<String> failures = new ArrayList<>();

        int start = 0;
        while (true) {
            String json = source.fetchPage(start, pageSize);
            int pageSourceCount = parser.sourceCount(json);
            if (pageSourceCount >= 0) {
                sourceCount = pageSourceCount;
            }
            List<NativeProvisioningTxnRecord> page = parser.parse(json);
            if (page.isEmpty()) {
                break;
            }
            for (NativeProvisioningTxnRecord rec : page) {
                txns++;
                try {
                    NativeProvisioningTxnRepository.UpsertOutcome outcome = sink.upsertTxn(rec);
                    if (outcome == NativeProvisioningTxnRepository.UpsertOutcome.INSERTED) {
                        txnInserted++;
                    } else {
                        txnUpdated++;
                    }
                } catch (SQLException | RuntimeException e) {
                    failed++;
                    if (failures.size() < 50) {
                        failures.add("txn " + rec.sourceId + ": " + e.getClass().getName());
                    }
                    continue; // skip items of a txn that failed to persist
                }
                for (NativeProvisioningItemRecord item : rec.items) {
                    items++;
                    try {
                        NativeProvisioningItemRepository.UpsertOutcome io = sink.upsertItem(item);
                        if (io == NativeProvisioningItemRepository.UpsertOutcome.INSERTED) {
                            itemInserted++;
                        } else {
                            itemUpdated++;
                        }
                    } catch (SQLException | RuntimeException e) {
                        failed++;
                        if (failures.size() < 50) {
                            failures.add("item under txn " + item.txnSourceId + " at index " + item.itemIndex
                                    + ": " + e.getClass().getName());
                        }
                    }
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        if (sourceCount >= 0 && txns != sourceCount) {
            throw new NativeImportException("Incomplete ProvisioningTransaction scan: sourceCount=" + sourceCount
                    + ", extracted=" + txns + ". No deletion action was taken.");
        }

        return new Result(txns, txnInserted, txnUpdated, items, itemInserted, itemUpdated, failed,
                sourceCount, 0, 0, false, false, failures);
    }
}
