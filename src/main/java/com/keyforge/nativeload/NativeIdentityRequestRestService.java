package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * KF Agent REST read service for native IdentityRequests ({@code sailpoint.object.IdentityRequest} →
 * {@code kf_identity_request}). Same architecture as the other KF Agent REST services: it reuses the
 * EXISTING native extraction path end-to-end — the plugin REST page source
 * ({@link NativeIdentityRequestClient}), {@link NativeIdentityRequestParser}, and the
 * {@link NativeIdentityRequestImportService} paging loop — and persists nothing (a non-JDBC
 * {@link CollectingSink} gathers the request records in memory instead of writing to PostgreSQL). The
 * native import also upserts nested items + approvals (separate tables/endpoints); this service collects
 * only the request rows and no-ops the item/approval upserts. No existing extraction or DB code is
 * modified or deleted; the DB write is bypassed by construction.
 *
 * <p><b>REST contract</b> (verified from {@link NativeIdentityRequestRecord} + {@code kf_identity_request}):
 * 34 SailPoint-facing fields. {@code errors} is the only {@code jsonb} field (returned as JSON) and is not
 * filterable; the other 33 are scalar (strings / booleans / integers / timestamps) and filterable. The
 * KeyForge {@code identityrequestid} PK, {@code record_hash}, {@code is_deleted}/{@code deleted_at} (not in
 * the native record) and the lineage envelope are excluded. This row has no {@code source_hash}, and the
 * native client has no server-side {@code modifiedAfter} support (full-scan only). The nested {@code items}
 * and {@code approvals} belong to their own entities and are NOT part of this response.
 */
public final class NativeIdentityRequestRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The {@code errors} jsonb field is NOT filterable. */
    private static final Map<String, Function<NativeIdentityRequestRecord, String>> SCALARS = buildScalars();

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

        List<NativeIdentityRequestRecord> all = collectAll(source);

        List<NativeIdentityRequestRecord> matched = new ArrayList<>();
        for (NativeIdentityRequestRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeIdentityRequestRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeIdentityRequestRecord r, Map<String, String> filters) {
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

    /** Walks the native source to completion via the existing import loop; a collecting sink = no DB. */
    private List<NativeIdentityRequestRecord> collectAll(NativeIdentityRequestPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep* methods are never invoked; no PostgreSQL is touched.
            new NativeIdentityRequestImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native identity-request extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing IdentityRequest fields, keyed by our DB column names (kf_identity_request order). */
    private Map<String, Object> toJson(NativeIdentityRequestRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("type", r.type);
        m.put("user_friendly_type", r.userFriendlyType);
        m.put("state", r.state);
        m.put("source", r.source);
        m.put("source_object", r.sourceObject);
        m.put("completion_status", r.completionStatus);
        m.put("execution_status", r.executionStatus);
        m.put("priority", r.priority);
        m.put("requester_id", r.requesterId);
        m.put("requester_display_name", r.requesterDisplayName);
        m.put("target_id", r.targetId);
        m.put("target_display_name", r.targetDisplayName);
        m.put("external_ticket_id", r.externalTicketId);
        m.put("process_id", r.processId);
        m.put("task_result_id", r.taskResultId);
        m.put("executing", r.executing);
        m.put("failure", r.failure);
        m.put("rejected", r.rejected);
        m.put("successful", r.successful);
        m.put("terminated", r.terminated);
        m.put("incomplete", r.incomplete);
        m.put("iiq_only", r.iiqOnly);
        m.put("provisioning_complete", r.provisioningComplete);
        m.put("end_date", iso(r.endDate));
        m.put("verified", iso(r.verified));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("errors", node(r.errorsJson));   // jsonb
        m.put("item_count", r.itemCount);
        m.put("approval_count", r.approvalCount);
        return m;
    }

    private static Map<String, Function<NativeIdentityRequestRecord, String>> buildScalars() {
        Map<String, Function<NativeIdentityRequestRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("type", r -> r.type);
        m.put("user_friendly_type", r -> r.userFriendlyType);
        m.put("state", r -> r.state);
        m.put("source", r -> r.source);
        m.put("source_object", r -> r.sourceObject);
        m.put("completion_status", r -> r.completionStatus);
        m.put("execution_status", r -> r.executionStatus);
        m.put("priority", r -> r.priority);
        m.put("requester_id", r -> r.requesterId);
        m.put("requester_display_name", r -> r.requesterDisplayName);
        m.put("target_id", r -> r.targetId);
        m.put("target_display_name", r -> r.targetDisplayName);
        m.put("external_ticket_id", r -> r.externalTicketId);
        m.put("process_id", r -> r.processId);
        m.put("task_result_id", r -> r.taskResultId);
        m.put("executing", r -> str(r.executing));
        m.put("failure", r -> str(r.failure));
        m.put("rejected", r -> str(r.rejected));
        m.put("successful", r -> str(r.successful));
        m.put("terminated", r -> str(r.terminated));
        m.put("incomplete", r -> str(r.incomplete));
        m.put("iiq_only", r -> str(r.iiqOnly));
        m.put("provisioning_complete", r -> str(r.provisioningComplete));
        m.put("end_date", r -> iso(r.endDate));
        m.put("verified", r -> iso(r.verified));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("item_count", r -> r.itemCount == null ? null : String.valueOf(r.itemCount));
        m.put("approval_count", r -> r.approvalCount == null ? null : String.valueOf(r.approvalCount));
        return m;
    }

    /** Re-hydrate a pre-serialized jsonb string into a JSON object/array; null stays null. */
    private Object node(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(json);
            return n == null || n.isNull() ? null : n;
        } catch (Exception e) {
            return json;
        }
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    private static String str(Boolean b) {
        return b == null ? null : b.toString();
    }

    /** In-memory sink: collects only request rows; item/approval upserts + sweeps are no-ops (no DB). */
    private static final class CollectingSink implements NativeIdentityRequestSink {
        final List<NativeIdentityRequestRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeIdentityRequestRepository.UpsertOutcome upsertRequest(NativeIdentityRequestRecord record) {
            records.add(record);
            return NativeIdentityRequestRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public NativeIdentityRequestItemRepository.UpsertOutcome upsertItem(NativeIdentityRequestItemRecord record) {
            // items are a separate entity; the identity-requests endpoint ignores them.
            return NativeIdentityRequestItemRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public NativeIdentityRequestApprovalRepository.UpsertOutcome upsertApproval(NativeIdentityRequestApprovalRecord record) {
            // approvals are a separate entity; the identity-requests endpoint ignores them.
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
