package com.keyforge.iiq.roleentitlement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists role→entitlement edges into the project-owned {@code kf_role_entitlement} table.
 * Consumes rows already built from the authoritative Role modeler source; duplicate
 * (role, application, property, value) grants collapse via the deterministic key. Each row
 * runs in its own savepoint. Zero rows is a valid outcome.
 */
public class RoleEntitlementPersistenceService {

    private final RoleEntitlementRepository repository;

    public RoleEntitlementPersistenceService() {
        this(new RoleEntitlementRepository());
    }

    public RoleEntitlementPersistenceService(String schema) {
        this(new RoleEntitlementRepository(schema));
    }

    public RoleEntitlementPersistenceService(RoleEntitlementRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    public static final class Result {
        private int input;
        private int distinctEdges;
        private int duplicatesCollapsed;
        private int resolvedEntitlementIds;
        private int unresolvedEntitlementIds;
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getInput() {
            return input;
        }

        public int getDistinctEdges() {
            return distinctEdges;
        }

        public int getDuplicatesCollapsed() {
            return duplicatesCollapsed;
        }

        public int getResolvedEntitlementIds() {
            return resolvedEntitlementIds;
        }

        public int getUnresolvedEntitlementIds() {
            return unresolvedEntitlementIds;
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

        public List<String> getFailures() {
            return failures;
        }
    }

    public Result persist(Connection conn, List<RoleEntitlementRow> rows) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();
        List<RoleEntitlementRow> input = rows == null ? List.of() : rows;
        result.input = input.size();

        Map<String, RoleEntitlementRow> byId = new LinkedHashMap<>();
        for (RoleEntitlementRow row : input) {
            if (byId.putIfAbsent(row.id(), row) != null) {
                result.duplicatesCollapsed++;
            }
        }
        List<RoleEntitlementRow> distinct = new ArrayList<>(byId.values());
        result.distinctEdges = distinct.size();
        for (RoleEntitlementRow r : distinct) {
            if (r.entitlementId() != null) {
                result.resolvedEntitlementIds++;
            } else {
                result.unresolvedEntitlementIds++;
            }
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (RoleEntitlementRow row : distinct) {
                persistOne(conn, row, result);
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

    private void persistOne(Connection conn, RoleEntitlementRow row, Result result) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            RoleEntitlementRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == RoleEntitlementRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("role=" + row.roleId() + " app=" + row.applicationName()
                    + " value=" + row.value() + " : " + e.getMessage());
        }
    }
}
