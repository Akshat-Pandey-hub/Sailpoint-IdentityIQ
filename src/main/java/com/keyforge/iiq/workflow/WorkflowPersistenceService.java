package com.keyforge.iiq.workflow;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists Workflow definitions into {@code kf_workflow_definition}. Each row in its own
 * savepoint. Approval-config columns are written NULL (not exposed by SCIM).
 */
public class WorkflowPersistenceService {

    private final WorkflowRepository repository;

    public WorkflowPersistenceService() {
        this(new WorkflowRepository());
    }

    public WorkflowPersistenceService(String schema) {
        this(new WorkflowRepository(schema));
    }

    public WorkflowPersistenceService(WorkflowRepository repository) {
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

    public Result persist(Connection conn, List<WorkflowDefinition> workflows) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (WorkflowDefinition wf : workflows == null ? List.<WorkflowDefinition>of() : workflows) {
                persistOne(conn, wf, result);
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

    private void persistOne(Connection conn, WorkflowDefinition wf, Result result) throws SQLException {
        WorkflowRow row;
        try {
            row = WorkflowRowMapper.map(wf);
        } catch (WorkflowMappingException e) {
            result.failed++;
            result.failures.add("workflow '" + wf.name() + "' (id=" + wf.id() + ") -> " + e.getMessage());
            return;
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            WorkflowRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == WorkflowRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("workflow '" + wf.name() + "' (id=" + wf.id() + ") -> " + e.getMessage());
        }
    }
}
