package com.keyforge.iiq.catalog;

import com.keyforge.iiq.model.Entitlement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists the requestable-entitlement catalog into migration_test.catalog.
 *
 * <p>Catalog identity is (name, appinstanceid), matching the target table's
 * unique constraint. Multiple IdentityIQ entitlement representations that
 * describe the same catalog item are collapsed into one catalog row, while
 * every contributing entitlement payload is preserved in customattributes.
 *
 * <p>The operation is idempotent because catalogid is deterministically derived
 * from the catalog identity and persistence uses an upsert on the primary key.
 */
public class CatalogPersistenceService {

    private final CatalogRepository repository;

    public CatalogPersistenceService() {
        this(new CatalogRepository());
    }

    public CatalogPersistenceService(String schema) {
        this(new CatalogRepository(schema));
    }

    public CatalogPersistenceService(CatalogRepository repository) {
        this.repository = repository;
    }

    /** Fully-qualified target table this service writes to (schema-qualified). */
    public String targetTable() {
        return repository.targetTable();
    }

    /** Counters and diagnostics for a persist run. */
    public static final class Result {
        private int derived;
        private int inserted;
        private int updated;
        private int failed;
        private int unresolvedEntitlements;
        private int unresolvedInstances;
        private int duplicateCatalogIds;
        private final List<String> failures = new ArrayList<>();

        public int getDerived() {
            return derived;
        }

        public int getInserted() {
            return inserted;
        }

        public int getUpdated() {
            return updated;
        }

        public int getFailed() {
            return failed;
        }

        public int getPersisted() {
            return inserted + updated;
        }

        public int getDistinctCatalogIds() {
            return derived - duplicateCatalogIds;
        }

        public int getUnresolvedEntitlements() {
            return unresolvedEntitlements;
        }

        public int getUnresolvedInstances() {
            return unresolvedInstances;
        }

        public List<String> getFailures() {
            return failures;
        }
    }

    /**
     * Derives logical catalog rows first, then persists those rows.
     *
     * <p>The input list may contain multiple IdentityIQ entitlement resources
     * representing the same logical catalog item. CatalogRowMapper performs
     * the grouping and deterministic primary selection before persistence.
     */
    public Result persist(Connection conn, List<Entitlement> entitlements) throws SQLException {
        repository.ensureTargetTable(conn);

        Set<String> existingEntitlementIds =
                repository.getExistingEntitlementIds(conn);
        Set<String> existingInstanceIds =
                repository.getExistingInstanceIds(conn);

        /*
         * Service #7 currently has no verified source for catalog.status.
         * Do not query or invent status values.
         */

        List<Entitlement> requestableEntitlements = entitlements.stream()
                .filter(e -> Boolean.TRUE.equals(e.getRequestable()))
                .toList();

        List<CatalogRow> rows = CatalogRowMapper.deriveCatalogRows(
                requestableEntitlements,
                existingEntitlementIds,
                existingInstanceIds
        );

        Result result = new Result();
        result.derived = rows.size();

        Set<String> seenCatalogIds = new HashSet<>();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            for (CatalogRow row : rows) {

                if (!seenCatalogIds.add(row.catalogid())) {
                    result.duplicateCatalogIds++;
                    continue;
                }

                if (row.entitlementid() == null) {
                    result.unresolvedEntitlements++;
                }

                if (row.appinstanceid() == null) {
                    result.unresolvedInstances++;
                }

                Savepoint savepoint = conn.setSavepoint();

                try {
                    CatalogRepository.UpsertOutcome outcome =
                            repository.upsert(conn, row);

                    conn.releaseSavepoint(savepoint);

                    if (outcome == CatalogRepository.UpsertOutcome.INSERTED) {
                        result.inserted++;
                    } else {
                        result.updated++;
                    }

                } catch (SQLException e) {
                    conn.rollback(savepoint);

                    result.failed++;
                    result.failures.add(
                            "catalog (id=" + row.catalogid() + ") -> " + e.getMessage()
                    );
                }
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
}