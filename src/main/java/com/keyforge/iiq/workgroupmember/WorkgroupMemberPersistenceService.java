package com.keyforge.iiq.workgroupmember;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists Workgroup → Identity memberships into the project-owned {@code kf_workgroup_member}
 * table. Consumes the authoritative memberships extracted by {@link WorkgroupMemberService};
 * duplicate (workgroup, identity) pairs collapse via the deterministic key. Each row runs in
 * its own savepoint.
 */
public class WorkgroupMemberPersistenceService {

    private final WorkgroupMemberRepository repository;

    public WorkgroupMemberPersistenceService() {
        this(new WorkgroupMemberRepository());
    }

    public WorkgroupMemberPersistenceService(String schema) {
        this(new WorkgroupMemberRepository(schema));
    }

    public WorkgroupMemberPersistenceService(WorkgroupMemberRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    public static final class Result {
        private int input;
        private int distinctEdges;
        private int duplicatesCollapsed;
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getInput() {
            return input;
        }

        public int getDistinctEdges() {
            return distinctEdges;
        }

        public int getDuplicatesCollapsed() {
            return duplicatesCollapsed;
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

    public Result persist(Connection conn, List<WorkgroupMembership> memberships) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();
        List<WorkgroupMembership> input = memberships == null ? List.of() : memberships;
        result.input = input.size();

        Map<String, WorkgroupMemberRow> byId = new LinkedHashMap<>();
        for (WorkgroupMembership m : input) {
            WorkgroupMemberRow row;
            try {
                row = WorkgroupMemberRowMapper.map(m);
            } catch (WorkgroupMemberMappingException e) {
                result.failed++;
                result.failures.add("workgroup=" + m.workgroupId() + " member=" + m.identityId()
                        + " : " + e.getMessage());
                continue;
            }
            if (byId.putIfAbsent(row.id(), row) != null) {
                result.duplicatesCollapsed++;
            }
        }
        List<WorkgroupMemberRow> rows = new ArrayList<>(byId.values());
        result.distinctEdges = rows.size();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (WorkgroupMemberRow row : rows) {
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

    private void persistOne(Connection conn, WorkgroupMemberRow row, Result result) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            WorkgroupMemberRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == WorkgroupMemberRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add("workgroup=" + row.workgroupId() + " identity=" + row.identityId()
                    + " : " + e.getMessage());
        }
    }
}
