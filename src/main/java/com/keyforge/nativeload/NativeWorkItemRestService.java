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
 * KF Agent REST read service for native work items ({@code sailpoint.object.WorkItem} → {@code kf_workitem}).
 * Reuses the EXISTING native extraction verbatim: the same {@link NativeWorkItemClient} page source +
 * {@link NativeWorkItemImportService} that the DB path uses, with a non-JDBC {@link CollectingSink} that
 * collects the records in memory (DB bypass). No existing extraction or DB code is modified; no PostgreSQL
 * is touched on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeWorkItemRecord} + {@link NativeWorkItemParser} +
 * {@code kf_workitem}): 41 SailPoint-facing fields in DB business-field order. Four {@code jsonb} fields
 * ({@code comments}/{@code sign_offs}/{@code owner_history}/{@code approval_set_items}) are returned as JSON;
 * {@code certification_related}/{@code expired}/{@code expirable} are booleans;
 * {@code escalation_count}/{@code reminders}/{@code reminders_sent}/{@code approval_set_item_count} are
 * integers; {@code expiration}/{@code expiration_date}/{@code notification}/{@code wake_up_date}/
 * {@code created_at}/{@code modified_at} are ISO-8601 timestamps; every other field is {@code text}. The four
 * jsonb fields are not filterable; the other 37 are scalar and filterable. The KeyForge {@code workitemid} PK,
 * {@code record_hash}, lineage envelope ({@code source_system}/{@code source_interface}/
 * {@code source_object_type}/{@code extraction_run_id}/{@code extracted_at}) and soft-delete columns are
 * excluded. No native {@code modifiedAfter} (the WorkItem client has no server-side modified filter →
 * full-scan only).
 */
public final class NativeWorkItemRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The four jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativeWorkItemRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeWorkItemPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeWorkItemRecord> all = collectAll(source);

        List<NativeWorkItemRecord> matched = new ArrayList<>();
        for (NativeWorkItemRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeWorkItemRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeWorkItemRecord r, Map<String, String> filters) {
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

    /** Walks the native source to completion via the EXISTING WorkItem import; collecting sink = no DB. */
    private List<NativeWorkItemRecord> collectAll(NativeWorkItemPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep method is never invoked; no PostgreSQL is touched.
            new NativeWorkItemImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native work-item extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing work-item fields, keyed by our DB column names (kf_workitem order). */
    private Map<String, Object> toJson(NativeWorkItemRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("type", r.type);
        m.put("state", r.state);
        m.put("level", r.level);
        m.put("requester_id", r.requesterId);
        m.put("requester_name", r.requesterName);
        m.put("assignee_id", r.assigneeId);
        m.put("assignee_name", r.assigneeName);
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("completer", r.completer);
        m.put("completion_comments", r.completionComments);
        m.put("handler", r.handler);
        m.put("notification_name", r.notificationName);
        m.put("identity_request_id", r.identityRequestId);
        m.put("target_id", r.targetId);
        m.put("target_name", r.targetName);
        m.put("certification_id", r.certificationId);
        m.put("certification_entity_id", r.certificationEntityId);
        m.put("certification_item_id", r.certificationItemId);
        m.put("entity_type", r.entityType);
        m.put("certification_related", r.certificationRelated);
        m.put("workflow_case_id", r.workflowCaseId);
        m.put("workflow_case_name", r.workflowCaseName);
        m.put("expiration", iso(r.expiration));
        m.put("expiration_date", iso(r.expirationDate));
        m.put("notification", iso(r.notification));
        m.put("wake_up_date", iso(r.wakeUpDate));
        m.put("escalation_count", r.escalationCount);
        m.put("reminders", r.reminders);
        m.put("reminders_sent", r.remindersSent);
        m.put("expired", r.expired);
        m.put("expirable", r.expirable);
        m.put("approval_set_item_count", r.approvalSetItemCount);
        m.put("comments", node(r.commentsJson));                 // jsonb
        m.put("sign_offs", node(r.signOffsJson));                // jsonb
        m.put("owner_history", node(r.ownerHistoryJson));        // jsonb
        m.put("approval_set_items", node(r.approvalSetItemsJson)); // jsonb
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeWorkItemRecord, String>> buildScalars() {
        Map<String, Function<NativeWorkItemRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("type", r -> r.type);
        m.put("state", r -> r.state);
        m.put("level", r -> r.level);
        m.put("requester_id", r -> r.requesterId);
        m.put("requester_name", r -> r.requesterName);
        m.put("assignee_id", r -> r.assigneeId);
        m.put("assignee_name", r -> r.assigneeName);
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("completer", r -> r.completer);
        m.put("completion_comments", r -> r.completionComments);
        m.put("handler", r -> r.handler);
        m.put("notification_name", r -> r.notificationName);
        m.put("identity_request_id", r -> r.identityRequestId);
        m.put("target_id", r -> r.targetId);
        m.put("target_name", r -> r.targetName);
        m.put("certification_id", r -> r.certificationId);
        m.put("certification_entity_id", r -> r.certificationEntityId);
        m.put("certification_item_id", r -> r.certificationItemId);
        m.put("entity_type", r -> r.entityType);
        m.put("certification_related", r -> r.certificationRelated == null ? null : r.certificationRelated.toString());
        m.put("workflow_case_id", r -> r.workflowCaseId);
        m.put("workflow_case_name", r -> r.workflowCaseName);
        m.put("expiration", r -> iso(r.expiration));
        m.put("expiration_date", r -> iso(r.expirationDate));
        m.put("notification", r -> iso(r.notification));
        m.put("wake_up_date", r -> iso(r.wakeUpDate));
        m.put("escalation_count", r -> r.escalationCount == null ? null : String.valueOf(r.escalationCount));
        m.put("reminders", r -> r.reminders == null ? null : String.valueOf(r.reminders));
        m.put("reminders_sent", r -> r.remindersSent == null ? null : String.valueOf(r.remindersSent));
        m.put("expired", r -> r.expired == null ? null : r.expired.toString());
        m.put("expirable", r -> r.expirable == null ? null : r.expirable.toString());
        m.put("approval_set_item_count",
                r -> r.approvalSetItemCount == null ? null : String.valueOf(r.approvalSetItemCount));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        return m;
    }

    /** Re-hydrate a pre-serialized jsonb string into a JSON value/object/array; null stays null. */
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

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeWorkItemSink {
        final List<NativeWorkItemRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeWorkItemRepository.UpsertOutcome upsert(NativeWorkItemRecord record) {
            records.add(record);
            return NativeWorkItemRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
