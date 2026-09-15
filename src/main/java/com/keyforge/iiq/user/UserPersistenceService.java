package com.keyforge.iiq.user;

import com.keyforge.iiq.model.Identity;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists IdentityIQ identities into the project-owned {@code usr} migration table.
 *
 * <p>All rows are written in a single transaction, but each row runs inside its own
 * savepoint so one bad row is reported and skipped without aborting the batch. The
 * transaction is committed once at the end.
 */
public class UserPersistenceService {

    private final UserRepository repository;

    public UserPersistenceService() {
        this(new UserRepository());
    }

    public UserPersistenceService(String schema) {
        this(new UserRepository(schema));
    }

    public UserPersistenceService(UserRepository repository) {
        this.repository = repository;
    }

    /** Fully-qualified target table this service writes to (schema-qualified). */
    public String targetTable() {
        return repository.targetTable();
    }

    /** Outcome counters and per-record failure messages for a persist run. */
    public static final class Result {
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

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

    public Result persist(Connection conn, List<Identity> identities) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Identity identity : identities) {
                persistOne(conn, identity, result);
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

    private void persistOne(Connection conn, Identity identity, Result result) throws SQLException {
        UserRow row;
        try {
            row = UserRowMapper.map(identity);
        } catch (UserMappingException e) {
            result.failed++;
            result.failures.add(describe(identity) + " -> " + e.getMessage());
            return;
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            UserRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == UserRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(describe(identity) + " -> " + e.getMessage());
        }
    }

    private static String describe(Identity identity) {
        String name = identity.getUserName() != null ? identity.getUserName() : "<no userName>";
        String id = identity.getId() != null ? identity.getId() : "<no id>";
        return "user '" + name + "' (id=" + id + ")";
    }
}
