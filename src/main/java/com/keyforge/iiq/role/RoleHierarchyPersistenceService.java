package com.keyforge.iiq.role;

import com.keyforge.iiq.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists role→role edges into the project-owned {@code kf_role_hierarchy} table,
 * derived from each Role's {@code inheritance}/{@code requirements}/{@code permits}.
 * Zero rows is a valid outcome when no role carries such relationships. Each edge runs
 * in its own savepoint.
 */
public class RoleHierarchyPersistenceService {

    private final RoleHierarchyRepository repository;

    public RoleHierarchyPersistenceService() {
        this(new RoleHierarchyRepository());
    }

    public RoleHierarchyPersistenceService(String schema) {
        this(new RoleHierarchyRepository(schema));
    }

    public RoleHierarchyPersistenceService(RoleHierarchyRepository repository) {
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

        public List<String> getFailures() {
            return failures;
        }
    }

    public Result persist(Connection conn, List<Role> roles) throws SQLException {
        repository.ensureTargetTable(conn);

        List<RoleHierarchyRow> rows = new ArrayList<>();
        for (Role role : roles) {
            rows.addAll(RoleHierarchyRowMapper.mapAll(role));
        }

        Result result = new Result();
        result.derived = rows.size();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (RoleHierarchyRow row : rows) {
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

    private void persistOne(Connection conn, RoleHierarchyRow row, Result result) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            RoleHierarchyRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == RoleHierarchyRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("edge " + row.roleId() + " -" + row.edgeType() + "-> "
                    + row.relatedRoleId() + " : " + e.getMessage());
        }
    }
}
