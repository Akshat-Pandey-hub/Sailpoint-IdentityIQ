package com.keyforge.iiq.entitlement;

import com.keyforge.iiq.model.Entitlement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Persists IdentityIQ entitlement definitions into the project-owned {@code entitlement}
 * table. The application relationship is resolved to an {@code instanceid}; unresolved
 * references leave the column NULL and are reported. Each row runs in its own savepoint.
 */
public class EntitlementPersistenceService {

    private final EntitlementRepository repository;

    public EntitlementPersistenceService() {
        this(new EntitlementRepository());
    }

    public EntitlementPersistenceService(String schema) {
        this(new EntitlementRepository(schema));
    }

    public EntitlementPersistenceService(EntitlementRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    /** Outcome counters, unresolved references, and failures. */
    public static final class Result {
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();
        private final List<String> unresolvedInstances = new ArrayList<>();

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

        public List<String> getFailures() {
            return failures;
        }

        public List<String> getUnresolvedInstances() {
            return unresolvedInstances;
        }
    }

    public Result persist(Connection conn, List<Entitlement> entitlements) throws SQLException {
        repository.ensureTargetTable(conn);

        Set<String> existingInstanceIds = repository.getExistingInstanceIds(conn);

        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Entitlement entitlement : entitlements) {
                persistOne(conn, entitlement, existingInstanceIds, result);
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

    private void persistOne(Connection conn,
                            Entitlement entitlement,
                            Set<String> existingInstanceIds,
                            Result result) throws SQLException {
        EntitlementRow row;
        try {
            row = EntitlementRowMapper.map(entitlement, existingInstanceIds);
        } catch (EntitlementMappingException e) {
            result.failed++;
            result.failures.add(describe(entitlement) + " -> " + e.getMessage());
            return;
        }

        if (row.instanceid() == null && EntitlementRowMapper.hasApplicationRef(entitlement)) {
            String label = entitlement.getApplication().getDisplayName() != null
                    ? entitlement.getApplication().getDisplayName() : entitlement.getApplication().getValue();
            result.unresolvedInstances.add(describe(entitlement)
                    + " -> application '" + label
                    + "' not found in " + repository.schema()
                    + ".applicationinstance (instanceid left NULL)");
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            EntitlementRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == EntitlementRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(describe(entitlement) + " -> " + e.getMessage());
        }
    }

    private static String describe(Entitlement entitlement) {
        String name = entitlement.getDisplayableName() != null ? entitlement.getDisplayableName()
                : (entitlement.getValue() != null ? entitlement.getValue() : "<no name>");
        String id = entitlement.getId() != null ? entitlement.getId() : "<no id>";
        return "entitlement '" + name + "' (id=" + id + ")";
    }
}
