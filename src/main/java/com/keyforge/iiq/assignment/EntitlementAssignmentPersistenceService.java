package com.keyforge.iiq.assignment;

import com.keyforge.iiq.model.AccountEntitlementAssignment;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists derived {@link AccountEntitlementAssignment}s into
 * {@code migration_test.entitlementassignment}.
 *
 * <p>The derivation itself is unchanged — this layer only maps valid relational
 * fields, preserves the complete provenance in {@code customattributes}, and upserts
 * idempotently on the deterministic primary key. Unresolvable account/entitlement
 * references become NULL columns (never fabricated) and are reported.
 */
public class EntitlementAssignmentPersistenceService {

    private final EntitlementAssignmentRepository repository;

    public EntitlementAssignmentPersistenceService() {
        this(new EntitlementAssignmentRepository());
    }

    public EntitlementAssignmentPersistenceService(String schema) {
        this(new EntitlementAssignmentRepository(schema));
    }

    public EntitlementAssignmentPersistenceService(EntitlementAssignmentRepository repository) {
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
        private int unresolvedAccounts;
        private int unresolvedEntitlements;
        private int duplicateAssignmentIds;
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

        /** Assignments whose account reference is not in &lt;schema&gt;.kf_account (accountid NULL). */
        public int getUnresolvedAccounts() {
            return unresolvedAccounts;
        }

        /** Assignments with no resolvable entitlement (entitlementid NULL). */
        public int getUnresolvedEntitlements() {
            return unresolvedEntitlements;
        }

        /** Distinct assignmentids seen (derived minus duplicate logical keys). */
        public int getDistinctAssignmentIds() {
            return derived - duplicateAssignmentIds;
        }

        public int getDuplicateAssignmentIds() {
            return duplicateAssignmentIds;
        }

        public List<String> getFailures() {
            return failures;
        }
    }

    public Result persist(Connection conn, List<AccountEntitlementAssignment> assignments) throws SQLException {
        repository.ensureTargetTable(conn);

        Set<String> existingAccountIds = repository.getExistingAccountIds(conn);
        Set<String> existingEntitlementIds = repository.getExistingEntitlementIds(conn);

        Result result = new Result();
        result.derived = assignments.size();
        Set<String> seenAssignmentIds = new HashSet<>();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (AccountEntitlementAssignment assignment : assignments) {
                persistOne(conn, assignment, existingAccountIds, existingEntitlementIds, seenAssignmentIds, result);
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
                            AccountEntitlementAssignment assignment,
                            Set<String> existingAccountIds,
                            Set<String> existingEntitlementIds,
                            Set<String> seenAssignmentIds,
                            Result result) throws SQLException {
        EntitlementAssignmentRow row =
                EntitlementAssignmentRowMapper.map(assignment, existingAccountIds, existingEntitlementIds);

        if (!seenAssignmentIds.add(row.assignmentid())) {
            // Two derived assignments produced the same logical key — reported, not hidden.
            result.duplicateAssignmentIds++;
        }
        if (row.accountid() == null && assignment.getAccountId() != null) {
            result.unresolvedAccounts++;
        }
        if (row.entitlementid() == null) {
            result.unresolvedEntitlements++;
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            EntitlementAssignmentRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == EntitlementAssignmentRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("assignment (account=" + assignment.getAccountId()
                    + ", entitlementValue=" + assignment.getEntitlementValue() + ") -> " + e.getMessage());
        }
    }
}
