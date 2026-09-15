package com.keyforge.iiq.eventlink;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives and persists the audit-event → object side of {@code kf_event_link}. Reads the already
 * persisted {@code kf_audit_event} targets and resolves each against the {@code usr}/{@code account}/
 * {@code entitlement} tables (no new IdentityIQ source). Every row runs in its own savepoint; a bad
 * record is reported and skipped, never dropped.
 *
 * <p>Events with a NULL/blank target carry no reference and produce no row (counted separately).
 * Unresolved and ambiguous targets ARE persisted (object id NULL, raw target kept) so no reference
 * silently disappears — the project's dangling-reference/quarantine approach.
 */
public class EventLinkPersistenceService {

    private final EventLinkRepository repository;

    public EventLinkPersistenceService() {
        this(new EventLinkRepository());
    }

    public EventLinkPersistenceService(String schema) {
        this(new EventLinkRepository(schema));
    }

    public EventLinkPersistenceService(EventLinkRepository repository) {
        this.repository = repository;
    }

    public EventLinkRepository repository() {
        return repository;
    }

    public static final class Result {
        private int examined;
        private int noTarget;
        private int resolvedIdentity;
        private int resolvedAccount;
        private int resolvedEntitlement;
        private int unresolved;
        private int ambiguous;
        private int outOfScope;
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> unresolvedTargets = new ArrayList<>();
        private final List<String> ambiguousTargets = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        public int getExamined() { return examined; }
        public int getNoTarget() { return noTarget; }
        public int getResolvedIdentity() { return resolvedIdentity; }
        public int getResolvedAccount() { return resolvedAccount; }
        public int getResolvedEntitlement() { return resolvedEntitlement; }
        public int getResolvedTotal() { return resolvedIdentity + resolvedAccount + resolvedEntitlement; }
        public int getUnresolved() { return unresolved; }
        public int getAmbiguous() { return ambiguous; }
        public int getOutOfScope() { return outOfScope; }
        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getPersisted() { return inserted + updated; }
        public int getFailed() { return failed; }
        public List<String> getUnresolvedTargets() { return unresolvedTargets; }
        public List<String> getAmbiguousTargets() { return ambiguousTargets; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn) throws SQLException {
        repository.ensureTargetTable(conn);

        Map<String, Set<String>> identityByName = repository.loadIdentityNameMap(conn);
        Map<String, Set<String>> accountByName = repository.loadAccountNameMap(conn);
        Map<String, Set<String>> entitlementByName = repository.loadEntitlementNameMap(conn);
        EventLinkResolver resolver = new EventLinkResolver(identityByName, accountByName, entitlementByName);

        List<EventLinkRepository.AuditTargetRef> refs = repository.readAuditTargets(conn);
        Result result = new Result();

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (EventLinkRepository.AuditTargetRef ref : refs) {
                persistOne(conn, ref, resolver, result);
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

    private void persistOne(Connection conn, EventLinkRepository.AuditTargetRef ref,
                            EventLinkResolver resolver, Result result) throws SQLException {
        result.examined++;
        String target = ref.target();
        if (target == null || target.isBlank()) {
            result.noTarget++; // no reference to link (e.g. ManualChange with empty target)
            return;
        }

        EventLinkResolver.Result resolution = resolver.resolve(target);
        EventLinkRow row;
        try {
            row = EventLinkRowMapper.map(ref.auditId(), target, resolution);
        } catch (EventLinkMappingException e) {
            result.failed++;
            result.failures.add("audit " + ref.auditId() + " target='" + target + "' -> " + e.getMessage());
            return;
        }

        switch (resolution.status()) {
            case RESOLVED -> {
                switch (resolution.targetType()) {
                    case "Identity" -> result.resolvedIdentity++;
                    case "Account" -> result.resolvedAccount++;
                    case "Entitlement" -> result.resolvedEntitlement++;
                    default -> { /* unreachable */ }
                }
            }
            case UNRESOLVED -> {
                result.unresolved++;
                result.unresolvedTargets.add(target);
            }
            case AMBIGUOUS -> {
                result.ambiguous++;
                result.ambiguousTargets.add(target + " -> " + resolution.rule());
            }
            case OUT_OF_SCOPE_TYPE -> result.outOfScope++;
        }

        Savepoint sp = conn.setSavepoint();
        try {
            if (repository.upsert(conn, row) == EventLinkRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
            conn.releaseSavepoint(sp);
        } catch (SQLException e) {
            conn.rollback(sp);
            result.failed++;
            result.failures.add("audit " + ref.auditId() + " target='" + target + "' -> " + e.getMessage());
        }
    }
}
