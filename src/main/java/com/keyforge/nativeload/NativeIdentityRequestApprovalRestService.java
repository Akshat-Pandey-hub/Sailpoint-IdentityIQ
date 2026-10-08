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
 * KF Agent REST read service for native IdentityRequest approval summaries
 * ({@code sailpoint.object.WorkflowSummary$ApprovalSummary} → {@code kf_identity_request_approval}). The
 * approvals are extracted as part of the SHARED IdentityRequest native import (each request carries nested
 * approval summaries); there is no separate approval client/import. This service therefore reuses
 * {@link NativeIdentityRequestClient} + {@link NativeIdentityRequestImportService} and a non-JDBC
 * {@link CollectingSink} that collects only the APPROVAL rows (no-ops request + item upserts). No existing
 * extraction or DB code is modified or deleted; the DB write is bypassed by construction.
 *
 * <p><b>REST contract</b> (verified from {@link NativeIdentityRequestApprovalRecord} +
 * {@code kf_identity_request_approval}): 17 SailPoint-facing fields. {@code comments} and {@code sign_off}
 * are {@code jsonb} (parser reads them with {@code json()}; DB shows {@code "[]"} / {@code "{}"}) and are
 * returned as JSON (array/object), not strings, and are NOT filterable. The other 15 are scalar (strings /
 * {@code approved} boolean / {@code approval_item_count}+{@code approval_index} integers / timestamps) and
 * filterable. The KeyForge {@code identityrequestapprovalid} PK (generated — the native record exposes no
 * business source id), {@code record_hash}, {@code is_deleted}/{@code deleted_at} (not in the native
 * record) and the lineage envelope are excluded. No {@code source_hash}; no native {@code modifiedAfter}
 * (full-scan only).
 */
public final class NativeIdentityRequestApprovalRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The 2 jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativeIdentityRequestApprovalRecord, String>> SCALARS = buildScalars();

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

        List<NativeIdentityRequestApprovalRecord> all = collectAll(source);

        List<NativeIdentityRequestApprovalRecord> matched = new ArrayList<>();
        for (NativeIdentityRequestApprovalRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeIdentityRequestApprovalRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeIdentityRequestApprovalRecord r, Map<String, String> filters) {
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
    private List<NativeIdentityRequestApprovalRecord> collectAll(NativeIdentityRequestPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep* methods are never invoked; no PostgreSQL is touched.
            new NativeIdentityRequestImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native identity-request-approval extraction failed: " + e.getMessage(), e);
        }
        return sink.approvals;
    }

    /** The SailPoint-facing approval fields, keyed by our DB column names (kf_identity_request_approval order). */
    private Map<String, Object> toJson(NativeIdentityRequestApprovalRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("request_source_id", r.requestSourceId);
        m.put("request_name", r.requestName);
        m.put("work_item_id", r.workItemId);
        m.put("work_item_type", r.workItemType);
        m.put("owner", r.owner);
        m.put("owner_id", r.ownerId);
        m.put("completer", r.completer);
        m.put("approved", r.approved);
        m.put("state", r.state);
        m.put("state_key", r.stateKey);
        m.put("type_key", r.typeKey);
        m.put("start_date", iso(r.startDate));
        m.put("end_date", iso(r.endDate));
        m.put("approval_item_count", r.approvalItemCount);
        m.put("approval_index", r.approvalIndex);
        m.put("comments", node(r.commentsJson));   // jsonb
        m.put("sign_off", node(r.signOffJson));     // jsonb
        return m;
    }

    private static Map<String, Function<NativeIdentityRequestApprovalRecord, String>> buildScalars() {
        Map<String, Function<NativeIdentityRequestApprovalRecord, String>> m = new LinkedHashMap<>();
        m.put("request_source_id", r -> r.requestSourceId);
        m.put("request_name", r -> r.requestName);
        m.put("work_item_id", r -> r.workItemId);
        m.put("work_item_type", r -> r.workItemType);
        m.put("owner", r -> r.owner);
        m.put("owner_id", r -> r.ownerId);
        m.put("completer", r -> r.completer);
        m.put("approved", r -> str(r.approved));
        m.put("state", r -> r.state);
        m.put("state_key", r -> r.stateKey);
        m.put("type_key", r -> r.typeKey);
        m.put("start_date", r -> iso(r.startDate));
        m.put("end_date", r -> iso(r.endDate));
        m.put("approval_item_count", r -> r.approvalItemCount == null ? null : String.valueOf(r.approvalItemCount));
        m.put("approval_index", r -> r.approvalIndex == null ? null : String.valueOf(r.approvalIndex));
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

    /** In-memory sink: collects only approval rows; request/item upserts + sweeps are no-ops (no DB). */
    private static final class CollectingSink implements NativeIdentityRequestSink {
        final List<NativeIdentityRequestApprovalRecord> approvals = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeIdentityRequestRepository.UpsertOutcome upsertRequest(NativeIdentityRequestRecord record) {
            return NativeIdentityRequestRepository.UpsertOutcome.INSERTED; // ignored
        }

        @Override
        public NativeIdentityRequestItemRepository.UpsertOutcome upsertItem(NativeIdentityRequestItemRecord record) {
            return NativeIdentityRequestItemRepository.UpsertOutcome.INSERTED; // ignored
        }

        @Override
        public NativeIdentityRequestApprovalRepository.UpsertOutcome upsertApproval(NativeIdentityRequestApprovalRecord record) {
            approvals.add(record);
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
