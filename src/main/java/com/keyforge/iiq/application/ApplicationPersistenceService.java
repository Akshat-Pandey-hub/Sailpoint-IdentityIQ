package com.keyforge.iiq.application;

import com.keyforge.iiq.model.Application;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists IdentityIQ applications into the project-owned {@code application} table.
 * Each row runs inside its own savepoint; the batch commits once at the end.
 */
public class ApplicationPersistenceService {

    private final ApplicationRepository repository;

    public ApplicationPersistenceService() {
        this(new ApplicationRepository());
    }

    public ApplicationPersistenceService(String schema) {
        this(new ApplicationRepository(schema));
    }

    public ApplicationPersistenceService(ApplicationRepository repository) {
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

    public Result persist(Connection conn, List<Application> applications) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Application application : applications) {
                persistOne(conn, application, result);
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

    private void persistOne(Connection conn, Application application, Result result) throws SQLException {
        ApplicationRow row;
        try {
            row = ApplicationRowMapper.map(application);
        } catch (ApplicationMappingException e) {
            result.failed++;
            result.failures.add(describe(application) + " -> " + e.getMessage());
            return;
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            ApplicationRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == ApplicationRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(describe(application) + " -> " + e.getMessage());
        }
    }

    private static String describe(Application application) {
        String name = application.getName() != null ? application.getName() : "<no name>";
        String id = application.getId() != null ? application.getId() : "<no id>";
        return "application '" + name + "' (id=" + id + ")";
    }
}
