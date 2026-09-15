package com.keyforge.iiq.provisioningtransaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists ProvisioningTransactions into {@code kf_provisioning_txn}. De-duplicates by canonical
 * {@code txnid} (first occurrence wins, duplicates reported), runs each row in its own savepoint, and
 * counts rows whose display timestamp could not be parsed (still persisted, with the raw value kept).
 * A bad record is reported and skipped, never dropped silently. No item-level rows are produced
 * (kf_provisioning_item is deferred; the source exposes no item data).
 */
public class ProvisioningTxnPersistenceService {

    private final ProvisioningTxnRepository repository;

    public ProvisioningTxnPersistenceService() {
        this(new ProvisioningTxnRepository());
    }

    public ProvisioningTxnPersistenceService(String schema) {
        this(new ProvisioningTxnRepository(schema));
    }

    public ProvisioningTxnPersistenceService(ProvisioningTxnRepository repository) {
        this.repository = repository;
    }

    public ProvisioningTxnRepository repository() {
        return repository;
    }

    public static final class Result {
        private int persisted;
        private int inserted;
        private int updated;
        private int failed;
        private int duplicates;
        private int timestampUnparsed;
        private final List<String> duplicateIds = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        public int getPersisted() { return persisted; }
        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getFailed() { return failed; }
        public int getDuplicates() { return duplicates; }
        public int getTimestampUnparsed() { return timestampUnparsed; }
        public List<String> getDuplicateIds() { return duplicateIds; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn, List<ProvisioningTransaction> transactions) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        Set<String> seen = new LinkedHashSet<>();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (ProvisioningTransaction t : transactions == null ? List.<ProvisioningTransaction>of() : transactions) {
                persistOne(conn, t, seen, result);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
        return result;
    }

    private void persistOne(Connection conn, ProvisioningTransaction t, Set<String> seen, Result result)
            throws SQLException {
        ProvisioningTxnRow row;
        try {
            row = ProvisioningTxnRowMapper.map(t);
        } catch (ProvisioningTxnMappingException e) {
            result.failed++;
            result.failures.add("txn id=" + t.id() + " -> " + e.getMessage());
            return;
        }

        if (!seen.add(row.txnid())) {
            result.duplicates++;
            result.duplicateIds.add(row.txnid() + " (source id " + t.id() + ")");
            return;
        }
        if (row.createdAt() == null && row.createdDisplay() != null) {
            result.timestampUnparsed++;
        }

        Savepoint sp = conn.setSavepoint();
        try {
            if (repository.upsert(conn, row) == ProvisioningTxnRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
            conn.releaseSavepoint(sp);
            result.persisted++;
        } catch (SQLException e) {
            conn.rollback(sp);
            result.failed++;
            result.failures.add("txn " + row.txnid() + " (source id " + t.id() + ") -> " + e.getMessage());
        }
    }
}
