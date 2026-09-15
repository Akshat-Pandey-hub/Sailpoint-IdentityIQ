package com.keyforge.iiq.taskresult;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists TaskResults into {@code kf_task_result}. Each row runs in its own savepoint; a bad record
 * is reported and skipped, never dropped. Idempotent via the deterministic {@code taskresultid} PK +
 * upsert. Empty source is a valid empty result.
 */
public class TaskResultPersistenceService {

    private final TaskResultRepository repository;

    public TaskResultPersistenceService() {
        this(new TaskResultRepository());
    }

    public TaskResultPersistenceService(String schema) {
        this(new TaskResultRepository(schema));
    }

    public TaskResultPersistenceService(TaskResultRepository repository) {
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

    public Result persist(Connection conn, List<TaskResult> taskResults) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (TaskResult t : taskResults == null ? List.<TaskResult>of() : taskResults) {
                persistOne(conn, t, result);
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

    private void persistOne(Connection conn, TaskResult t, Result result) throws SQLException {
        TaskResultRow row;
        try {
            row = TaskResultRowMapper.map(t);
        } catch (TaskResultMappingException e) {
            result.failed++;
            result.failures.add("taskresult '" + t.name() + "' (id=" + t.id() + ") -> " + e.getMessage());
            return;
        }
        Savepoint savepoint = conn.setSavepoint();
        try {
            TaskResultRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == TaskResultRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("taskresult '" + t.name() + "' (id=" + t.id() + ") -> " + e.getMessage());
        }
    }
}
