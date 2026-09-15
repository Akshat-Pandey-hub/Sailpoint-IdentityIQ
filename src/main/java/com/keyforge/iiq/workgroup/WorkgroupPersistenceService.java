package com.keyforge.iiq.workgroup;

import com.keyforge.iiq.model.UserGroup;
import com.keyforge.iiq.usergroup.UserGroupService;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the <b>Workgroup</b> subset of the extracted User Groups into the project-owned
 * {@code kf_workgroup} table. It reuses the unchanged {@link UserGroupService} extraction:
 * the caller passes the full extraction and this service selects only records of type
 * Workgroup (Populations and Groups are intentionally excluded — they are not
 * {@code kf_workgroup}). Each row runs in its own savepoint. The existing {@code usergroup}
 * table and its persistence are untouched.
 */
public class WorkgroupPersistenceService {

    private final WorkgroupRepository repository;

    public WorkgroupPersistenceService() {
        this(new WorkgroupRepository());
    }

    public WorkgroupPersistenceService(String schema) {
        this(new WorkgroupRepository(schema));
    }

    public WorkgroupPersistenceService(WorkgroupRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    /** Selects only the Workgroup-type records (never Populations/Groups). Testable, DB-free. */
    public static List<UserGroup> workgroupsOnly(List<UserGroup> all) {
        List<UserGroup> workgroups = new ArrayList<>();
        if (all == null) {
            return workgroups;
        }
        for (UserGroup g : all) {
            if (UserGroupService.TYPE_WORKGROUP.equalsIgnoreCase(g.getType())) {
                workgroups.add(g);
            }
        }
        return workgroups;
    }

    public static final class Result {
        private int workgroups;
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getWorkgroups() {
            return workgroups;
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

    /**
     * @param allUserGroups the full extraction (Workgroups + Populations + Groups); only
     *                      the Workgroup subset is written to {@code kf_workgroup}
     */
    public Result persist(Connection conn, List<UserGroup> allUserGroups) throws SQLException {
        repository.ensureTargetTable(conn);

        List<UserGroup> workgroups = workgroupsOnly(allUserGroups);
        Result result = new Result();
        result.workgroups = workgroups.size();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (UserGroup group : workgroups) {
                persistOne(conn, group, result);
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

    private void persistOne(Connection conn, UserGroup group, Result result) throws SQLException {
        WorkgroupRow row;
        try {
            row = WorkgroupRowMapper.map(group);
        } catch (WorkgroupMappingException e) {
            result.failed++;
            result.failures.add(describe(group) + " -> " + e.getMessage());
            return;
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            WorkgroupRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == WorkgroupRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(describe(group) + " -> " + e.getMessage());
        }
    }

    private static String describe(UserGroup group) {
        String name = group.getName() != null ? group.getName() : "<no name>";
        String id = group.getId() != null ? group.getId() : "<no id>";
        return "workgroup '" + name + "' (id=" + id + ")";
    }
}
