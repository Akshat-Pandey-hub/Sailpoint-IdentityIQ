package com.keyforge.iiq.workitem;

import com.keyforge.iiq.model.WorkItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists extracted {@link WorkItem}s into the project-owned {@code workitem} table.
 * Each row runs inside its own savepoint; the batch commits once at the end. Idempotent
 * on the deterministic primary key {@code id}.
 */
public class WorkItemPersistenceService {

    private final WorkItemRepository repository;

    public WorkItemPersistenceService() {
        this(new WorkItemRepository());
    }

    public WorkItemPersistenceService(String schema) {
        this(new WorkItemRepository(schema));
    }

    public WorkItemPersistenceService(WorkItemRepository repository) {
        this.repository = repository;
    }

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

    public Result persist(Connection conn, List<WorkItem> workItems) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (WorkItem workItem : workItems) {
                persistOne(conn, workItem, result);
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

    private void persistOne(Connection conn, WorkItem workItem, Result result) throws SQLException {
        WorkItemRow row;
        try {
            row = WorkItemRowMapper.map(workItem);
        } catch (WorkItemMappingException e) {
            result.failed++;
            result.failures.add(describe(workItem) + " -> " + e.getMessage());
            return;
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            WorkItemRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == WorkItemRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(describe(workItem) + " -> " + e.getMessage());
        }
    }

    private static String describe(WorkItem workItem) {
        String name = workItem.getWorkItemName() != null ? workItem.getWorkItemName() : "<no name>";
        String id = workItem.getId() != null ? workItem.getId() : "<no id>";
        return "workitem '" + name + "' (id=" + id + ")";
    }
}
