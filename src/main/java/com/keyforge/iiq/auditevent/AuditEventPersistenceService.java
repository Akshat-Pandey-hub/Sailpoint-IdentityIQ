package com.keyforge.iiq.auditevent;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists AuditEvents into {@code kf_audit_event}. Each row runs in its own savepoint; a bad
 * record is reported and skipped, never dropped silently.
 *
 * <p>Duplicate handling is deterministic: events are de-duplicated by their canonical
 * {@code auditid} (the first occurrence wins), and every duplicate is recorded and reported rather
 * than upserting twice. The timestamp limitation is surfaced too — rows whose {@code created}
 * string could not be parsed with the one verified pattern are counted (they still persist, with a
 * NULL {@code created_at} and the verbatim {@code created_display}).
 */
public class AuditEventPersistenceService {

    private final AuditEventRepository repository;

    public AuditEventPersistenceService() {
        this(new AuditEventRepository());
    }

    public AuditEventPersistenceService(String schema) {
        this(new AuditEventRepository(schema));
    }

    public AuditEventPersistenceService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public AuditEventRepository repository() {
        return repository;
    }

    public static final class Result {
        private int persisted;
        private int inserted;
        private int updated;
        private int failed;
        private int duplicates;
        private int timestampUnparsed;
        private final List<String> duplicateIds = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        public int getPersisted() { return persisted; }
        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getFailed() { return failed; }
        public int getDuplicates() { return duplicates; }
        public int getTimestampUnparsed() { return timestampUnparsed; }
        public List<String> getDuplicateIds() { return duplicateIds; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn, List<AuditEvent> events) throws SQLException {
        repository.ensureTargetTable(conn);
        Result result = new Result();
        Set<String> seen = new LinkedHashSet<>();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (AuditEvent e : events == null ? List.<AuditEvent>of() : events) {
                persistOne(conn, e, seen, result);
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

    private void persistOne(Connection conn, AuditEvent e, Set<String> seen, Result result) throws SQLException {
        AuditEventRow row;
        try {
            row = AuditEventRowMapper.map(e);
        } catch (AuditEventMappingException ex) {
            result.failed++;
            result.failures.add("audit event id=" + e.id() + " -> " + ex.getMessage());
            return;
        }

        // Deterministic de-duplication by canonical auditid: first occurrence wins.
        if (!seen.add(row.auditid())) {
            result.duplicates++;
            result.duplicateIds.add(row.auditid() + " (source id " + e.id() + ")");
            return;
        }

        // Surface the timestamp limitation: parsed to NULL but a raw value was present.
        if (row.createdAt() == null && row.createdDisplay() != null) {
            result.timestampUnparsed++;
        }

        Savepoint sp = conn.setSavepoint();
        try {
            AuditEventRepository.UpsertOutcome outcome = repository.upsert(conn, row);
            conn.releaseSavepoint(sp);
            result.persisted++;
            if (outcome == AuditEventRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
        } catch (SQLException ex) {
            conn.rollback(sp);
            result.failed++;
            result.failures.add("audit event " + row.auditid() + " (source id " + e.id() + ") -> " + ex.getMessage());
        }
    }
}
