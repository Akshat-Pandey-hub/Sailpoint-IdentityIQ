package com.keyforge.iiq.event;

import com.keyforge.iiq.runledger.RunLedger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the append-only derivation of {@code kf_event} from the persisted event-source tables.
 * Reads only (no IIQ calls); each append runs in its own savepoint so one bad row never aborts the
 * batch. Never updates or deletes existing events. {@code extraction_run_id} is taken from
 * {@link RunLedger#currentRunId()} (may be null outside a wrapped {@code -db} command).
 */
public class EventPersistenceService {

    private final EventRepository repository;

    public EventPersistenceService() {
        this(new EventRepository());
    }

    public EventPersistenceService(String schema) {
        this(new EventRepository(schema));
    }

    public EventPersistenceService(String eventSchema, String workItemArchiveSchema) {
        this(new EventRepository(eventSchema, workItemArchiveSchema));
    }

    public EventPersistenceService(EventRepository repository) {
        this.repository = repository;
    }

    public String targetTable() {
        return repository.table();
    }

    public static final class Result {
        private int derived;
        private int appended;
        private int deduped;
        private int failed;
        private final Map<String, Integer> derivedByType = new LinkedHashMap<>();
        private final List<String> failures = new ArrayList<>();

        public int getDerived() { return derived; }
        public int getAppended() { return appended; }
        public int getDeduped() { return deduped; }
        public int getFailed() { return failed; }
        public Map<String, Integer> getDerivedByType() { return derivedByType; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn) throws SQLException {
        repository.ensureTargetTable(conn);

        List<EventRow> rows = repository.deriveFromSources(conn, RunLedger.currentRunId());
        Result result = new Result();
        result.derived = rows.size();
        for (EventRow row : rows) {
            result.derivedByType.merge(row.srcObjectType(), 1, Integer::sum);
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (EventRow row : rows) {
                appendOne(conn, row, result);
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

    private void appendOne(Connection conn, EventRow row, Result result) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try {
            boolean inserted = repository.append(conn, row);
            conn.releaseSavepoint(savepoint);
            if (inserted) {
                result.appended++;
            } else {
                result.deduped++;
            }
        } catch (SQLException e) {
            conn.rollback(savepoint);
            result.failed++;
            result.failures.add(row.srcObjectType() + " " + row.srcObjectId() + " : " + e.getMessage());
        }
    }
}
