package com.keyforge.iiq.provisioningtransaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists provisioning items into {@code kf_provisioning_item}. Each row runs in its own savepoint;
 * a bad record is reported and skipped, never dropped. Idempotent via the deterministic {@code itemid}
 * PK + upsert. Empty source is a valid empty result.
 */
public class ProvisioningItemPersistenceService {

    private final ProvisioningItemRepository repository;

    public ProvisioningItemPersistenceService() {
        this(new ProvisioningItemRepository());
    }

    public ProvisioningItemPersistenceService(String schema) {
        this(new ProvisioningItemRepository(schema));
    }

    public ProvisioningItemPersistenceService(ProvisioningItemRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    public static final class Result {
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getFailed() { return failed; }
        public int getPersisted() { return inserted + updated; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn, List<ProvisioningItem> items) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (ProvisioningItem it : items == null ? List.<ProvisioningItem>of() : items) {
                persistOne(conn, it, result);
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

    private void persistOne(Connection conn, ProvisioningItem it, Result result) throws SQLException {
        ProvisioningItemRow row;
        try {
            row = ProvisioningItemRowMapper.map(it);
        } catch (ProvisioningItemMappingException e) {
            result.failed++;
            result.failures.add("item txn=" + it.parentTransactionId() + " " + it.requestType()
                    + "[" + it.itemIndex() + "] -> " + e.getMessage());
            return;
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            ProvisioningItemRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == ProvisioningItemRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("item txn=" + it.parentTransactionId() + " " + it.requestType()
                    + "[" + it.itemIndex() + "] -> " + e.getMessage());
        }
    }
}
