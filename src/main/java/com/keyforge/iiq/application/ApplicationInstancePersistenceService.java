package com.keyforge.iiq.application;

import com.keyforge.iiq.model.Application;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Persists IdentityIQ applications into the {@code migration_test.applicationinstance}
 * table (one instance per application).
 *
 * <p>All rows are written in a single transaction, but each row runs inside its own
 * savepoint so one bad row is reported and skipped without aborting the batch. FK
 * columns are resolved against the migration tables; unresolved owner/application
 * relationships leave the column NULL and are reported (never invented).
 */
public class ApplicationInstancePersistenceService {

    private final ApplicationInstanceRepository repository;

    public ApplicationInstancePersistenceService() {
        this(new ApplicationInstanceRepository());
    }

    public ApplicationInstancePersistenceService(String schema) {
        this(new ApplicationInstanceRepository(schema));
    }

    public ApplicationInstancePersistenceService(ApplicationInstanceRepository repository) {
        this.repository = repository;
    }

    /** Fully-qualified target table this service writes to (schema-qualified). */
    public String targetTable() {
        return repository.targetTable();
    }

    /** Outcome counters, unresolved relationships, and failures for a persist run. */
    public static final class Result {
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();
        private final List<String> unresolvedOwners = new ArrayList<>();
        private final List<String> applicationsNotFound = new ArrayList<>();

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

        /** Instances whose owner could not be matched to a &lt;schema&gt;.kf_identity user (ownerid NULL). */
        public List<String> getUnresolvedOwners() {
            return unresolvedOwners;
        }

        /** Instances whose application is not in &lt;schema&gt;.kf_application (applicationid NULL). */
        public List<String> getApplicationsNotFound() {
            return applicationsNotFound;
        }
    }

    public Result persist(Connection conn, List<Application> applications) throws SQLException {
        repository.ensureTargetTable(conn);

        Set<String> existingApplicationIds = repository.getExistingApplicationIds(conn);
        Set<String> existingUserIds = repository.getExistingUserIds(conn);

        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (Application application : applications) {
                persistOne(conn, application, existingApplicationIds, existingUserIds, result);
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

    private void persistOne(Connection conn,
                            Application application,
                            Set<String> existingApplicationIds,
                            Set<String> existingUserIds,
                            Result result) throws SQLException {
        ApplicationInstanceRow row;
        try {
            row = ApplicationInstanceRowMapper.map(application, existingApplicationIds, existingUserIds);
        } catch (ApplicationInstanceMappingException e) {
            result.failed++;
            result.failures.add(describe(application) + " -> " + e.getMessage());
            return;
        }

        // Surface unresolved relationships (the row still persists with NULL FKs).
        if (row.applicationid() == null) {
            result.applicationsNotFound.add(describe(application)
                    + " -> application not found in " + repository.schema()
                    + ".kf_application (applicationid left NULL)");
        }
        if (row.ownerId() == null && ApplicationInstanceRowMapper.hasOwnerReference(application)) {
            String ownerLabel = application.getOwner().getDisplayName() != null
                    ? application.getOwner().getDisplayName() : application.getOwner().getValue();
            result.unresolvedOwners.add(describe(application)
                    + " -> owner '" + ownerLabel + "' not found in " + repository.schema()
                    + ".kf_identity (ownerid left NULL)");
        }

        Savepoint savepoint = conn.setSavepoint();
        try {
            ApplicationInstanceRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == ApplicationInstanceRepository.UpsertOutcome.INSERTED) {
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
