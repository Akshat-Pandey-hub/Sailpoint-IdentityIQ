package com.keyforge.iiq.violation;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists PolicyViolations into {@code kf_violation}. Each row in its own savepoint. Zero rows
 * is a valid outcome (the source currently has no violations).
 */
public class ViolationPersistenceService {

    private final ViolationRepository repository;

    public ViolationPersistenceService() {
        this(new ViolationRepository());
    }

    public ViolationPersistenceService(String schema) {
        this(new ViolationRepository(schema));
    }

    public ViolationPersistenceService(ViolationRepository repository) {
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

    public Result persist(Connection conn, List<PolicyViolation> violations) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (PolicyViolation v : violations == null ? List.<PolicyViolation>of() : violations) {
                persistOne(conn, v, result);
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

    private void persistOne(Connection conn, PolicyViolation v, Result result) throws SQLException {
        ViolationRow row;
        try {
            row = ViolationRowMapper.map(v);
        } catch (ViolationMappingException e) {
            result.failed++;
            result.failures.add("violation id=" + v.id() + " -> " + e.getMessage());
            return;
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            ViolationRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == ViolationRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("violation id=" + v.id() + " -> " + e.getMessage());
        }
    }
}
