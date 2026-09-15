package com.keyforge.iiq.account;

import com.keyforge.iiq.model.Account;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Persists IdentityIQ accounts into the project-owned {@code account} table. FK
 * columns are resolved against the migration tables; unresolved identity/application
 * relationships leave the column NULL and are reported (never invented). Each row runs
 * in its own savepoint; the batch commits once at the end.
 */
public class AccountPersistenceService {

    private final AccountRepository repository;

    public AccountPersistenceService() {
        this(new AccountRepository());
    }

    public AccountPersistenceService(String schema) {
        this(new AccountRepository(schema));
    }

    public AccountPersistenceService(AccountRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    /** Outcome counters, unresolved relationships, and failures. */
    public static final class Result {
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();
        private final List<String> unresolvedUsers = new ArrayList<>();
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

        /** Accounts whose identity could not be matched to a user (userid NULL). */
        public List<String> getUnresolvedUsers() {
            return unresolvedUsers;
        }

        /** Accounts whose application is not a known instance (instanceid NULL). */
        public List<String> getUnresolvedInstances() {
            return unresolvedInstances;
        }
    }

    public Result persist(Connection conn, List<Account> accounts) throws SQLException {
        repository.ensureTargetTable(conn);

        Set<String> existingUserIds = repository.getExistingUserIds(conn);
        Set<String> existingInstanceIds = repository.getExistingInstanceIds(conn);

        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Account account : accounts) {
                persistOne(conn, account, existingUserIds, existingInstanceIds, result);
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
                            Account account,
                            Set<String> existingUserIds,
                            Set<String> existingInstanceIds,
                            Result result) throws SQLException {
        AccountRow row;
        try {
            row = AccountRowMapper.map(account, existingUserIds, existingInstanceIds);
        } catch (AccountMappingException e) {
            result.failed++;
            result.failures.add(describe(account) + " -> " + e.getMessage());
            return;
        }

        if (row.userid() == null && AccountRowMapper.hasIdentityRef(account)) {
            String label = account.getIdentity().getDisplayName() != null
                    ? account.getIdentity().getDisplayName() : account.getIdentity().getValue();
            result.unresolvedUsers.add(describe(account)
                    + " -> identity '" + label + "' not found in " + repository.schema()
                    + ".kf_identity (userid left NULL)");
        }
        if (row.instanceid() == null && AccountRowMapper.hasApplicationRef(account)) {
            String label = account.getApplication().getDisplayName() != null
                    ? account.getApplication().getDisplayName() : account.getApplication().getValue();
            result.unresolvedInstances.add(describe(account)
                    + " -> application '" + label
                    + "' not found in " + repository.schema()
                    + ".applicationinstance (instanceid left NULL)");
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            AccountRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == AccountRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(describe(account) + " -> " + e.getMessage());
        }
    }

    private static String describe(Account account) {
        String name = account.getNativeIdentity() != null ? account.getNativeIdentity()
                : (account.getDisplayName() != null ? account.getDisplayName() : "<no name>");
        String id = account.getId() != null ? account.getId() : "<no id>";
        return "account '" + name + "' (id=" + id + ")";
    }
}
