package com.keyforge.iiq.accessrequest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the Access Request aggregate into {@code kf_access_request} + {@code kf_request_item}
 * + {@code kf_request_approval}. Each row runs in its own savepoint; a bad record is reported and
 * skipped, never dropped silently. Items and approvals carry {@code requestid} back to their
 * request; approvals also carry the authoritative WorkItem/WorkItemArchive linkage.
 */
public class AccessRequestPersistenceService {

    private final AccessRequestRepository repository;

    public AccessRequestPersistenceService() {
        this(new AccessRequestRepository());
    }

    public AccessRequestPersistenceService(String schema) {
        this(new AccessRequestRepository(schema));
    }

    public AccessRequestPersistenceService(AccessRequestRepository repository) {
        this.repository = repository;
    }

    public AccessRequestRepository repository() {
        return repository;
    }

    public static final class Result {
        private int requests, items, approvals;
        private int requestsInserted, itemsInserted, approvalsInserted;
        private int failed;
        private final List<String> failures = new ArrayList<>();

        public int getRequests() { return requests; }
        public int getItems() { return items; }
        public int getApprovals() { return approvals; }
        public int getRequestsInserted() { return requestsInserted; }
        public int getItemsInserted() { return itemsInserted; }
        public int getApprovalsInserted() { return approvalsInserted; }
        public int getFailed() { return failed; }
        public int getPersisted() { return requests + items + approvals; }
        public List<String> getFailures() { return failures; }
    }

    public Result persist(Connection conn, List<AccessRequest> requests) throws SQLException {
        repository.ensureTargetTables(conn);
        Result result = new Result();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (AccessRequest r : requests == null ? List.<AccessRequest>of() : requests) {
                persistRequest(conn, r, result);
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

    private void persistRequest(Connection conn, AccessRequest r, Result result) throws SQLException {
        // request row
        try {
            AccessRequestRow row = AccessRequestRowMapper.mapRequest(r);
            Savepoint sp = conn.setSavepoint();
            try {
                if (repository.upsertRequest(conn, row) == AccessRequestRepository.UpsertOutcome.INSERTED) {
                    result.requestsInserted++;
                }
                conn.releaseSavepoint(sp);
                result.requests++;
            } catch (SQLException e) {
                conn.rollback(sp);
                result.failed++;
                result.failures.add("request " + r.requestId() + " -> " + e.getMessage());
                return; // if the request row failed, skip its children
            }
        } catch (AccessRequestMappingException e) {
            result.failed++;
            result.failures.add("request " + r.requestId() + " -> " + e.getMessage());
            return;
        }

        // items
        List<AccessRequest.Item> items = r.items() == null ? List.of() : r.items();
        for (AccessRequest.Item it : items) {
            try {
                RequestItemRow row = AccessRequestRowMapper.mapItem(r, it);
                Savepoint sp = conn.setSavepoint();
                try {
                    if (repository.upsertItem(conn, row) == AccessRequestRepository.UpsertOutcome.INSERTED) {
                        result.itemsInserted++;
                    }
                    conn.releaseSavepoint(sp);
                    result.items++;
                } catch (SQLException e) {
                    conn.rollback(sp);
                    result.failed++;
                    result.failures.add("item " + it.id() + " (req " + r.requestId() + ") -> " + e.getMessage());
                }
            } catch (AccessRequestMappingException e) {
                result.failed++;
                result.failures.add("item " + it.id() + " (req " + r.requestId() + ") -> " + e.getMessage());
            }
        }

        // approvals (interactions)
        List<AccessRequest.Approval> approvals = r.interactions() == null ? List.of() : r.interactions();
        for (int i = 0; i < approvals.size(); i++) {
            try {
                RequestApprovalRow row = AccessRequestRowMapper.mapApproval(r, approvals.get(i), i);
                Savepoint sp = conn.setSavepoint();
                try {
                    if (repository.upsertApproval(conn, row) == AccessRequestRepository.UpsertOutcome.INSERTED) {
                        result.approvalsInserted++;
                    }
                    conn.releaseSavepoint(sp);
                    result.approvals++;
                } catch (SQLException e) {
                    conn.rollback(sp);
                    result.failed++;
                    result.failures.add("approval (req " + r.requestId() + ") -> " + e.getMessage());
                }
            } catch (AccessRequestMappingException e) {
                result.failed++;
                result.failures.add("approval (req " + r.requestId() + ") -> " + e.getMessage());
            }
        }
    }
}
