package com.keyforge.iiq.provisioningtransaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives and persists the provisioning-transaction side of {@code kf_event_link}. For each
 * ProvisioningTransaction it emits a link only from the transaction's OWN authoritative references:
 * a Request link from {@code accessRequestId} (resolved against {@code kf_access_request}) and a
 * Certification link from {@code certificationName} (always UNRESOLVED — certifications are not
 * extracted). A transaction with neither reference produces no row (counted separately). Nothing is
 * inferred from timestamps, identities, accounts, or access-request records. Each row runs in its own
 * savepoint; the deterministic PK makes reruns idempotent.
 */
public class ProvisioningEventLinkPersistenceService {

    private final ProvisioningEventLinkRepository repository;

    public ProvisioningEventLinkPersistenceService() {
        this(new ProvisioningEventLinkRepository());
    }

    public ProvisioningEventLinkPersistenceService(String schema) {
        this(new ProvisioningEventLinkRepository(schema));
    }

    public ProvisioningEventLinkPersistenceService(ProvisioningEventLinkRepository repository) {
        this.repository = repository;
    }

    public ProvisioningEventLinkRepository repository() {
        return repository;
    }

    public static final class Result {
        private int examined;
        private int requestResolved;
        private int requestUnresolved;
        private int requestAmbiguous;
        private int certificationUnresolved;
        private int noReference;
        private int inserted;
        private int updated;
        private int failed;
        private final List<String> unresolvedRequestRefs = new ArrayList<>();
        private final List<String> ambiguousRequestRefs = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        public int getExamined() { return examined; }
        public int getRequestResolved() { return requestResolved; }
        public int getRequestUnresolved() { return requestUnresolved; }
        public int getRequestAmbiguous() { return requestAmbiguous; }
        public int getCertificationUnresolved() { return certificationUnresolved; }
        public int getNoReference() { return noReference; }
        public int getInserted() { return inserted; }
        public int getUpdated() { return updated; }
        public int getPersisted() { return inserted + updated; }
        public int getFailed() { return failed; }
        public List<String> getUnresolvedRequestRefs() { return unresolvedRequestRefs; }
        public List<String> getAmbiguousRequestRefs() { return ambiguousRequestRefs; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn, List<ProvisioningTransaction> transactions) throws SQLException {
        repository.ensureTargetTable(conn);
        Map<String, Set<String>> requestByKey = repository.loadRequestRefMap(conn);
        ProvisioningLinkResolver resolver = new ProvisioningLinkResolver(requestByKey);

        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (ProvisioningTransaction t : transactions == null ? List.<ProvisioningTransaction>of() : transactions) {
                persistOne(conn, t, resolver, result);
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

    private void persistOne(Connection conn, ProvisioningTransaction t,
                            ProvisioningLinkResolver resolver, Result result) throws SQLException {
        result.examined++;
        boolean hasRequestRef = t.accessRequestId() != null && !t.accessRequestId().isBlank();
        boolean hasCertRef = t.certificationName() != null && !t.certificationName().isBlank();

        if (!hasRequestRef && !hasCertRef) {
            result.noReference++; // transaction carries no Request/Certification reference → no link
            return;
        }

        if (hasRequestRef) {
            ProvisioningLinkResolver.Result r = resolver.resolveRequest(t.accessRequestId());
            switch (r.status()) {
                case RESOLVED -> result.requestResolved++;
                case UNRESOLVED -> {
                    result.requestUnresolved++;
                    result.unresolvedRequestRefs.add(t.accessRequestId());
                }
                case AMBIGUOUS -> {
                    result.requestAmbiguous++;
                    result.ambiguousRequestRefs.add(t.accessRequestId() + " -> " + r.rule());
                }
            }
            upsert(conn, ProvisioningEventLinkRowMapper.mapRequestLink(t, r), t, "request", result);
        }

        if (hasCertRef) {
            result.certificationUnresolved++;
            upsert(conn, ProvisioningEventLinkRowMapper.mapCertificationLink(t), t, "certification", result);
        }
    }

    private void upsert(Connection conn, ProvisioningEventLinkRow row, ProvisioningTransaction t,
                        String kind, Result result) throws SQLException {
        Savepoint sp = conn.setSavepoint();
        try {
            if (repository.upsert(conn, row) == ProvisioningEventLinkRepository.UpsertOutcome.INSERTED) {
                result.inserted++;
            } else {
                result.updated++;
            }
            conn.releaseSavepoint(sp);
        } catch (SQLException e) {
            conn.rollback(sp);
            result.failed++;
            result.failures.add(kind + " link for txn " + t.id() + " -> " + e.getMessage());
        }
    }
}
