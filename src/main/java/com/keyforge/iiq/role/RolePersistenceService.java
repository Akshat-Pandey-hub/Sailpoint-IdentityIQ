package com.keyforge.iiq.role;

import com.keyforge.iiq.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists IdentityIQ Roles into the project-owned {@code kf_role} table. Each row runs
 * in its own savepoint so one bad record is reported and skipped without aborting the
 * batch. Mirrors {@link com.keyforge.iiq.entitlement.EntitlementPersistenceService}.
 */
public class RolePersistenceService {

    private final RoleRepository repository;

    public RolePersistenceService() {
        this(new RoleRepository());
    }

    public RolePersistenceService(String schema) {
        this(new RoleRepository(schema));
    }

    public RolePersistenceService(RoleRepository repository) {
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

    public Result persist(Connection conn, List<Role> roles) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Role role : roles) {
                persistOne(conn, role, result);
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

    private void persistOne(Connection conn, Role role, Result result) throws SQLException {
        RoleRow row;
        try {
            row = RoleRowMapper.map(role);
        } catch (RoleMappingException e) {
            result.failed++;
            result.failures.add(describe(role) + " -> " + e.getMessage());
            return;
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            RoleRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == RoleRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(describe(role) + " -> " + e.getMessage());
        }
    }

    private static String describe(Role role) {
        String name = role.getDisplayableName() != null ? role.getDisplayableName()
                : (role.getName() != null ? role.getName() : "<no name>");
        String id = role.getId() != null ? role.getId() : "<no id>";
        return "role '" + name + "' (id=" + id + ")";
    }
}
