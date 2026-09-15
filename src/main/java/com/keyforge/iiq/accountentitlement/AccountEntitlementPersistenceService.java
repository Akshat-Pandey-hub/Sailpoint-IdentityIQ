package com.keyforge.iiq.accountentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the account→entitlement projection into {@code kf_account_entitlement}. Consumes
 * the already-derived {@link AccountEntitlementAssignment} list (same input that feeds the
 * existing {@code entitlementassignment} table), so no new IdentityIQ source is introduced.
 * Each row runs in its own savepoint.
 */
public class AccountEntitlementPersistenceService {

    private final AccountEntitlementRepository repository;

    public AccountEntitlementPersistenceService() {
        this(new AccountEntitlementRepository());
    }

    public AccountEntitlementPersistenceService(String schema) {
        this(new AccountEntitlementRepository(schema));
    }

    public AccountEntitlementPersistenceService(AccountEntitlementRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    public static final class Result {
        private int derived;
        private int inserted;
        private int updated;
        private int failed;
        private int resolved;
        private int unresolved;
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

        public int getResolved() {
            return resolved;
        }

        public int getUnresolved() {
            return unresolved;
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
        List<AccountEntitlementRow> rows = new ArrayList<>();
        for (AccountEntitlementAssignment a : assignments == null ? List.<AccountEntitlementAssignment>of() : assignments) {
            AccountEntitlementRow row = AccountEntitlementRowMapper.map(a);
            rows.add(row);
            if ("RESOLVED".equals(row.resolutionStatus())) {
                result.resolved++;
            } else {
                result.unresolved++;
            }
        }
        result.derived = rows.size();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (AccountEntitlementRow row : rows) {
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

    private void persistOne(Connection conn, AccountEntitlementRow row, Result result) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            AccountEntitlementRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == AccountEntitlementRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("account=" + row.accountId() + " entitlement=" + row.entitlementValue()
                    + " : " + e.getMessage());
        }
    }
}
