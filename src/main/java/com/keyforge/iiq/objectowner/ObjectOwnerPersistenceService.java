package com.keyforge.iiq.objectowner;

import com.keyforge.iiq.model.Application;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalises verified object→owner relationships from Applications, Roles and Entitlements
 * into the project-owned {@code kf_object_owner} table. Pure aggregation over the existing
 * (unchanged) source services; each edge runs in its own savepoint. Objects with no owner
 * yield no edge. Workgroups are intentionally NOT a source here — the live Workgroup
 * DataSource provides no owner, so their ownership is unavailable (not invented).
 */
public class ObjectOwnerPersistenceService {

    private final ObjectOwnerRepository repository;

    public ObjectOwnerPersistenceService() {
        this(new ObjectOwnerRepository());
    }

    public ObjectOwnerPersistenceService(String schema) {
        this(new ObjectOwnerRepository(schema));
    }

    public ObjectOwnerPersistenceService(ObjectOwnerRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.targetTable();
    }

    /** Builds all owner edges from the three sources. Testable, DB-free. */
    public static List<ObjectOwnerRow> buildRows(List<Application> applications,
                                                 List<Role> roles,
                                                 List<Entitlement> entitlements) {
        List<ObjectOwnerRow> rows = new ArrayList<>();
        if (applications != null) {
            for (Application a : applications) {
                ObjectOwnerRowMapper.fromApplication(a).ifPresent(rows::add);
            }
        }
        if (roles != null) {
            for (Role r : roles) {
                ObjectOwnerRowMapper.fromRole(r).ifPresent(rows::add);
            }
        }
        if (entitlements != null) {
            for (Entitlement e : entitlements) {
                ObjectOwnerRowMapper.fromEntitlement(e).ifPresent(rows::add);
            }
        }
        return rows;
    }

    public static final class Result {
        private int applicationOwners;
        private int roleOwners;
        private int entitlementOwners;
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getApplicationOwners() {
            return applicationOwners;
        }

        public int getRoleOwners() {
            return roleOwners;
        }

        public int getEntitlementOwners() {
            return entitlementOwners;
        }

        public int getDerived() {
            return applicationOwners + roleOwners + entitlementOwners;
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

    public Result persist(Connection conn,
                          List<Application> applications,
                          List<Role> roles,
                          List<Entitlement> entitlements) throws SQLException {
        repository.ensureTargetTable(conn);

        Result result = new Result();
        List<ObjectOwnerRow> rows = new ArrayList<>();
        for (Application a : applications == null ? List.<Application>of() : applications) {
            ObjectOwnerRowMapper.fromApplication(a).ifPresent(r -> {
                rows.add(r);
                result.applicationOwners++;
            });
        }
        for (Role r : roles == null ? List.<Role>of() : roles) {
            ObjectOwnerRowMapper.fromRole(r).ifPresent(row -> {
                rows.add(row);
                result.roleOwners++;
            });
        }
        for (Entitlement e : entitlements == null ? List.<Entitlement>of() : entitlements) {
            ObjectOwnerRowMapper.fromEntitlement(e).ifPresent(row -> {
                rows.add(row);
                result.entitlementOwners++;
            });
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (ObjectOwnerRow row : rows) {
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

    private void persistOne(Connection conn, ObjectOwnerRow row, Result result) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            ObjectOwnerRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(savepoint);
            if (outcome == ObjectOwnerRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(row.objectType() + " '" + row.objectName() + "' (id=" + row.objectId()
                    + ") owner=" + row.ownerId() + " : " + e.getMessage());
        }
    }
}
