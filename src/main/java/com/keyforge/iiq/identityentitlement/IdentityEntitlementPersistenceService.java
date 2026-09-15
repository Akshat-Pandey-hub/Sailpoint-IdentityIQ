package com.keyforge.iiq.identityentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists the identity→entitlement projection into {@code kf_identity_entitlement}. Consumes
 * the already-derived {@link AccountEntitlementAssignment} list (no new IdentityIQ source).
 *
 * <p>The grain is the distinct identity→entitlement edge: assignments that resolve to the same
 * (identity, application, source_attribute, value) — e.g. the same value held via two accounts —
 * collapse to one edge (correct identity-level semantics). Assignments with no identity are not
 * identity edges and are skipped (counted). Each edge runs in its own savepoint.
 */
public class IdentityEntitlementPersistenceService {

    private final IdentityEntitlementRepository repository;

    public IdentityEntitlementPersistenceService() {
        this(new IdentityEntitlementRepository());
    }

    public IdentityEntitlementPersistenceService(String schema) {
        this(new IdentityEntitlementRepository(schema));
    }

    public IdentityEntitlementPersistenceService(IdentityEntitlementRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    /** Distinct identity→entitlement edges (deduped by deterministic PK). Testable, DB-free. */
    public static List<IdentityEntitlementRow> buildDistinctRows(List<AccountEntitlementAssignment> assignments) {
        Map<String, IdentityEntitlementRow> byId = new LinkedHashMap<>();
        for (AccountEntitlementAssignment a : assignments == null ? List.<AccountEntitlementAssignment>of() : assignments) {
            Optional<IdentityEntitlementRow> row = IdentityEntitlementRowMapper.map(a);
            row.ifPresent(r -> byId.putIfAbsent(r.id(), r));
        }
        return new ArrayList<>(byId.values());
    }

    public static final class Result {
        private int inputAssignments;
        private int skippedNoIdentity;
        private int duplicatesCollapsed;
        private int distinctEdges;
        private int resolved;
        private int unresolved;
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getInputAssignments() {
            return inputAssignments;
        }

        public int getSkippedNoIdentity() {
            return skippedNoIdentity;
        }

        public int getDuplicatesCollapsed() {
            return duplicatesCollapsed;
        }

        public int getDistinctEdges() {
            return distinctEdges;
        }

        public int getResolved() {
            return resolved;
        }

        public int getUnresolved() {
            return unresolved;
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

    public Result persist(Connection conn, List<AccountEntitlementAssignment> assignments) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();
        List<AccountEntitlementAssignment> input = assignments == null ? List.of() : assignments;
        result.inputAssignments = input.size();

        Map<String, IdentityEntitlementRow> byId = new LinkedHashMap<>();
        for (AccountEntitlementAssignment a : input) {
            Optional<IdentityEntitlementRow> mapped = IdentityEntitlementRowMapper.map(a);
            if (mapped.isEmpty()) {
                result.skippedNoIdentity++;
                continue;
            }
            IdentityEntitlementRow row = mapped.get();
            if (byId.putIfAbsent(row.id(), row) != null) {
                result.duplicatesCollapsed++;
            }
        }
        List<IdentityEntitlementRow> rows = new ArrayList<>(byId.values());
        result.distinctEdges = rows.size();
        for (IdentityEntitlementRow r : rows) {
            if ("RESOLVED".equals(r.resolutionStatus())) {
                result.resolved++;
            } else {
                result.unresolved++;
            }
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (IdentityEntitlementRow row : rows) {
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

    private void persistOne(Connection conn, IdentityEntitlementRow row, Result result) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            IdentityEntitlementRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == IdentityEntitlementRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("identity=" + row.identityId() + " entitlement=" + row.entitlementValue()
                    + " : " + e.getMessage());
        }
    }
}
