package com.keyforge.iiq.policy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists Policy definitions into {@code kf_policy}. Each row in its own savepoint. Zero rows
 * is a valid outcome (the source currently has no policies).
 */
public class PolicyPersistenceService {

    private final PolicyRepository repository;

    public PolicyPersistenceService() {
        this(new PolicyRepository());
    }

    public PolicyPersistenceService(String schema) {
        this(new PolicyRepository(schema));
    }

    public PolicyPersistenceService(PolicyRepository repository) {
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

    public Result persist(Connection conn, List<PolicyDefinition> policies) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (PolicyDefinition p : policies == null ? List.<PolicyDefinition>of() : policies) {
                persistOne(conn, p, result);
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

    private void persistOne(Connection conn, PolicyDefinition p, Result result) throws SQLException {
        PolicyRow row;
        try {
            row = PolicyRowMapper.map(p);
        } catch (PolicyMappingException e) {
            result.failed++;
            result.failures.add("policy '" + p.name() + "' (id=" + p.id() + ") -> " + e.getMessage());
            return;
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            PolicyRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == PolicyRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("policy '" + p.name() + "' (id=" + p.id() + ") -> " + e.getMessage());
        }
    }
}
