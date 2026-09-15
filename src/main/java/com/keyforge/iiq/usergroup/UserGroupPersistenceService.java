package com.keyforge.iiq.usergroup;

import com.keyforge.iiq.model.UserGroup;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists extracted {@link UserGroup}s into {@code migration_test.usergroup}.
 * Creates the schema/table on demand, then upserts each record on its deterministic
 * {@code id} (idempotent). Uses the same transaction + savepoint-per-row style as
 * the other persistence services.
 */
public class UserGroupPersistenceService {

    private final UserGroupRepository repository;

    public UserGroupPersistenceService() {
        this(new UserGroupRepository());
    }

    public UserGroupPersistenceService(String schema) {
        this(new UserGroupRepository(schema));
    }

    public UserGroupPersistenceService(UserGroupRepository repository) {
        this.repository = repository;
    }

    /** Fully-qualified target table this service writes to (schema-qualified). */
    public String targetTable() {
        return repository.targetTable();
    }

    /** Counters and diagnostics for a persist run. */
    public static final class Result {
        private int derived;
        private int inserted;
        private int updated;
        private int failed;
        private final Map<String, Integer> byType = new LinkedHashMap<>();
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

        public int countOf(String type) {
            return byType.getOrDefault(type, 0);
        }

        public List<String> getFailures() {
            return failures;
        }
    }

    public Result persist(Connection conn, List<UserGroup> groups) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (UserGroup group : groups) {
                UserGroupRow row;
                try {
                    row = UserGroupRowMapper.map(group);
                } catch (UserGroupMappingException e) {
                    result.failed++;
                    result.failures.add("map (" + group.getType() + " id=" + group.getId()
                            + ") -> " + e.getMessage());
                    continue;
                }

                result.derived++;
                result.byType.merge(group.getType(), 1, Integer::sum);

                Savepoint savepoint = conn.setSavepoint();
                try {
                    UserGroupRepository.UpsertOutcome outcome = repository.upsert(conn, row);
                    conn.releaseSavepoint(savepoint);
                    if (outcome == UserGroupRepository.UpsertOutcome.INSERTED) {
                        result.inserted++;
                    } else {
                        result.updated++;
                    }
                } catch (SQLException e) {
                    conn.rollback(savepoint);
                    result.failed++;
                    result.failures.add("usergroup (" + row.sourceType() + " id=" + row.id()
                            + ", name=" + row.name() + ") -> " + e.getMessage());
                }
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
}
