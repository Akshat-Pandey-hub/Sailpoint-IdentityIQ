package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KF Agent REST read service for native IdentityRequestItems ({@code sailpoint.object.IdentityRequestItem}
 * → {@code kf_identity_request_item}). The items are extracted as part of the SHARED IdentityRequest
 * native import (each request carries nested items); there is no separate item client/import. This service
 * therefore reuses {@link NativeIdentityRequestClient} + {@link NativeIdentityRequestImportService} and a
 * non-JDBC {@link CollectingSink} that collects only the ITEM rows (no-ops request + approval upserts).
 * No existing extraction or DB code is modified or deleted; the DB write is bypassed by construction.
 *
 * <p><b>REST contract</b> (verified from {@link NativeIdentityRequestItemRecord} + {@code kf_identity_request_item}):
 * 33 SailPoint-facing fields, ALL scalar (strings / booleans / an integer {@code retries} / timestamps) —
 * there are no nested/jsonb fields ({@code expansion} is a boolean; {@code expansion_cause}/{@code expansion_info}
 * are text), so every field is filterable. The KeyForge {@code identityrequestitemid} PK, {@code record_hash},
 * {@code is_deleted}/{@code deleted_at} (not in the native record) and the lineage envelope are excluded.
 * This row has no {@code source_hash}, and the native client has no server-side {@code modifiedAfter}
 * support (full-scan only).
 */
public final class NativeIdentityRequestItemRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    /** Response field name -> value extractor (all scalar, all filterable). */
    private static final Map<String, Function<NativeIdentityRequestItemRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeIdentityRequestPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeIdentityRequestItemRecord> all = collectAll(source);

        List<NativeIdentityRequestItemRecord> matched = new ArrayList<>();
        for (NativeIdentityRequestItemRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeIdentityRequestItemRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeIdentityRequestItemRecord r, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> f : filters.entrySet()) {
            String actual = SCALARS.get(f.getKey()).apply(r);
            if (actual == null || !actual.equals(f.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** Walks the native source to completion via the EXISTING IdentityRequest import; collecting sink = no DB. */
    private List<NativeIdentityRequestItemRecord> collectAll(NativeIdentityRequestPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep* methods are never invoked; no PostgreSQL is touched.
            new NativeIdentityRequestImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native identity-request-item extraction failed: " + e.getMessage(), e);
        }
        return sink.items;
    }

    /** The SailPoint-facing item fields, keyed by our DB column names (kf_identity_request_item order). */
    private Map<String, Object> toJson(NativeIdentityRequestItemRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("request_source_id", r.requestSourceId);
        m.put("request_name", r.requestName);
        m.put("application", r.application);
        m.put("attribute_name", r.attributeName);
        m.put("attribute_value", r.attributeValue);
        m.put("operation", r.operation);
        m.put("managed_attribute_type", r.managedAttributeType);
        m.put("assignment_id", r.assignmentId);
        m.put("native_identity", r.nativeIdentity);
        m.put("instance", r.instance);
        m.put("approver_name", r.approverName);
        m.put("approval_state", r.approvalState);
        m.put("approved", r.approved);
        m.put("approval_complete", r.approvalComplete);
        m.put("rejected", r.rejected);
        m.put("provisioning_state", r.provisioningState);
        m.put("provisioning_engine", r.provisioningEngine);
        m.put("provisioning_request_id", r.provisioningRequestId);
        m.put("provisioning_complete", r.provisioningComplete);
        m.put("provisioning_failed", r.provisioningFailed);
        m.put("compilation_status", r.compilationStatus);
        m.put("owner_name", r.ownerName);
        m.put("requester_comments", r.requesterComments);
        m.put("expansion", r.expansion);
        m.put("expansion_cause", r.expansionCause);
        m.put("expansion_info", r.expansionInfo);
        m.put("retries", r.retries);
        m.put("iiq", r.iiq);
        m.put("start_date", iso(r.startDate));
        m.put("end_date", iso(r.endDate));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeIdentityRequestItemRecord, String>> buildScalars() {
        Map<String, Function<NativeIdentityRequestItemRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("request_source_id", r -> r.requestSourceId);
        m.put("request_name", r -> r.requestName);
        m.put("application", r -> r.application);
        m.put("attribute_name", r -> r.attributeName);
        m.put("attribute_value", r -> r.attributeValue);
        m.put("operation", r -> r.operation);
        m.put("managed_attribute_type", r -> r.managedAttributeType);
        m.put("assignment_id", r -> r.assignmentId);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("instance", r -> r.instance);
        m.put("approver_name", r -> r.approverName);
        m.put("approval_state", r -> r.approvalState);
        m.put("approved", r -> str(r.approved));
        m.put("approval_complete", r -> str(r.approvalComplete));
        m.put("rejected", r -> str(r.rejected));
        m.put("provisioning_state", r -> r.provisioningState);
        m.put("provisioning_engine", r -> r.provisioningEngine);
        m.put("provisioning_request_id", r -> r.provisioningRequestId);
        m.put("provisioning_complete", r -> str(r.provisioningComplete));
        m.put("provisioning_failed", r -> str(r.provisioningFailed));
        m.put("compilation_status", r -> r.compilationStatus);
        m.put("owner_name", r -> r.ownerName);
        m.put("requester_comments", r -> r.requesterComments);
        m.put("expansion", r -> str(r.expansion));
        m.put("expansion_cause", r -> r.expansionCause);
        m.put("expansion_info", r -> r.expansionInfo);
        m.put("retries", r -> r.retries == null ? null : String.valueOf(r.retries));
        m.put("iiq", r -> str(r.iiq));
        m.put("start_date", r -> iso(r.startDate));
        m.put("end_date", r -> iso(r.endDate));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    private static String str(Boolean b) {
        return b == null ? null : b.toString();
    }

    /** In-memory sink: collects only item rows; request/approval upserts + sweeps are no-ops (no DB). */
    private static final class CollectingSink implements NativeIdentityRequestSink {
        final List<NativeIdentityRequestItemRecord> items = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeIdentityRequestRepository.UpsertOutcome upsertRequest(NativeIdentityRequestRecord record) {
            // the request itself is a separate entity; the items endpoint ignores it.
            return NativeIdentityRequestRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public NativeIdentityRequestItemRepository.UpsertOutcome upsertItem(NativeIdentityRequestItemRecord record) {
            items.add(record);
            return NativeIdentityRequestItemRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public NativeIdentityRequestApprovalRepository.UpsertOutcome upsertApproval(NativeIdentityRequestApprovalRecord record) {
            // approvals are a separate entity; the items endpoint ignores them.
            return NativeIdentityRequestApprovalRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweepRequests(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweepApprovals(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
