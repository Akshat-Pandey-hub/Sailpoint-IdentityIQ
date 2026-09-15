package com.keyforge.iiq.identityrole;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists Identity&rarr;Role assignments into {@code kf_identity_role}. Each row runs in its own
 * savepoint; a bad record is reported and skipped, never dropped. Idempotent via the deterministic
 * {@code id} PK + upsert. Empty source is a valid empty result.
 */
public class IdentityRolePersistenceService {

    private final IdentityRoleRepository repository;

    public IdentityRolePersistenceService() {
        this(new IdentityRoleRepository());
    }

    public IdentityRolePersistenceService(String schema) {
        this(new IdentityRoleRepository(schema));
    }

    public IdentityRolePersistenceService(IdentityRoleRepository repository) {
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

    public Result persist(Connection conn, List<IdentityRoleAssignment> assignments) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (IdentityRoleAssignment a : assignments == null ? List.<IdentityRoleAssignment>of() : assignments) {
                persistOne(conn, a, result);
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

    private void persistOne(Connection conn, IdentityRoleAssignment a, Result result) throws SQLException {
        IdentityRoleRow row;
        try {
            row = IdentityRoleRowMapper.map(a);
        } catch (IdentityRoleMappingException e) {
            result.failed++;
            result.failures.add("identity=" + a.identityId() + " role=" + a.roleId() + " -> " + e.getMessage());
            return;
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            IdentityRoleRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == IdentityRoleRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("identity=" + a.identityId() + " role=" + a.roleId() + " -> " + e.getMessage());
        }
    }
}
