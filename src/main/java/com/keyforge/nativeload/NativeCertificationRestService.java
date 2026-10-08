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
 * KF Agent REST read service for native certifications ({@code sailpoint.object.Certification} →
 * {@code kf_certification}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativeCertificationClient} page source + {@link NativeCertificationImportService} that the DB path
 * uses, with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No existing
 * extraction or DB code is modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeCertificationRecord} + {@link NativeCertificationParser}
 * + {@code kf_certification}): 43 SailPoint-facing fields in DB business-field order. Four structured fields
 * are returned as real JSON: {@code certifiers}/{@code sign_off_history} (parser {@code json()}) and
 * {@code allowed_statuses}/{@code tags} (parser {@code text()} but the native mapper stores a serialized JSON
 * array string, so they are re-hydrated too). {@code complete}/{@code expired}/{@code continuous}/
 * {@code electronically_signed} are booleans; {@code signed}/{@code finished}/{@code activated}/
 * {@code expiration}/{@code created_at}/{@code modified_at} are ISO-8601 timestamps (NOT booleans);
 * {@code total_items}/{@code completed_items}/{@code open_items}/{@code total_entities}/
 * {@code completed_entities}/{@code open_entities}/{@code percent_complete} are integers;
 * {@code automatic_closing_date} is plain {@code text} (not a timestamp); every other field is {@code text}.
 * The four structured fields are not filterable; the other 39 are scalar and filterable. The KeyForge
 * {@code certificationid} PK, {@code record_hash}, lineage envelope and soft-delete columns are excluded. No
 * native {@code modifiedAfter} (the certification client has no server-side modified filter → full-scan only).
 */
public final class NativeCertificationRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The four structured fields are NOT filterable. */
    private static final Map<String, Function<NativeCertificationRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeCertificationPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeCertificationRecord> all = collectAll(source);

        List<NativeCertificationRecord> matched = new ArrayList<>();
        for (NativeCertificationRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeCertificationRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeCertificationRecord r, Map<String, String> filters) {
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

    /** Walks the native source to completion via the EXISTING Certification import; collecting sink = no DB. */
    private List<NativeCertificationRecord> collectAll(NativeCertificationPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep method is never invoked; no PostgreSQL is touched.
            new NativeCertificationImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native certification extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing certification fields, keyed by our DB column names (kf_certification order). */
    private Map<String, Object> toJson(NativeCertificationRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("certification_name", r.certificationName);
        m.put("short_name", r.shortName);
        m.put("type", r.type);
        m.put("phase", r.phase);
        m.put("comments", r.comments);
        m.put("creator", r.creator);
        m.put("manager", r.manager);
        m.put("certification_group_id", r.certificationGroupId);
        m.put("certification_group_name", r.certificationGroupName);
        m.put("certification_definition_id", r.certificationDefinitionId);
        m.put("group_definition_id", r.groupDefinitionId);
        m.put("group_definition_name", r.groupDefinitionName);
        m.put("application_id", r.applicationId);
        m.put("task_schedule_id", r.taskScheduleId);
        m.put("trigger_id", r.triggerId);
        m.put("parent_id", r.parentId);
        m.put("complete", r.complete);
        m.put("expired", r.expired);
        m.put("continuous", r.continuous);
        m.put("electronically_signed", r.electronicallySigned);
        m.put("signed", iso(r.signed));
        m.put("finished", iso(r.finished));
        m.put("activated", iso(r.activated));
        m.put("expiration", iso(r.expiration));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("total_items", r.totalItems);
        m.put("completed_items", r.completedItems);
        m.put("open_items", r.openItems);
        m.put("total_entities", r.totalEntities);
        m.put("completed_entities", r.completedEntities);
        m.put("open_entities", r.openEntities);
        m.put("percent_complete", r.percentComplete);
        m.put("certifiers", node(r.certifiersJson));              // structured JSON
        m.put("sign_off_history", node(r.signOffHistoryJson));    // structured JSON
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("approver_rule", r.approverRule);
        m.put("automatic_closing_date", r.automaticClosingDate);
        m.put("allowed_statuses", node(r.allowedStatusesJson));   // structured JSON (serialized array string)
        m.put("tags", node(r.tagsJson));                          // structured JSON (serialized array string)
        return m;
    }

    private static Map<String, Function<NativeCertificationRecord, String>> buildScalars() {
        Map<String, Function<NativeCertificationRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("certification_name", r -> r.certificationName);
        m.put("short_name", r -> r.shortName);
        m.put("type", r -> r.type);
        m.put("phase", r -> r.phase);
        m.put("comments", r -> r.comments);
        m.put("creator", r -> r.creator);
        m.put("manager", r -> r.manager);
        m.put("certification_group_id", r -> r.certificationGroupId);
        m.put("certification_group_name", r -> r.certificationGroupName);
        m.put("certification_definition_id", r -> r.certificationDefinitionId);
        m.put("group_definition_id", r -> r.groupDefinitionId);
        m.put("group_definition_name", r -> r.groupDefinitionName);
        m.put("application_id", r -> r.applicationId);
        m.put("task_schedule_id", r -> r.taskScheduleId);
        m.put("trigger_id", r -> r.triggerId);
        m.put("parent_id", r -> r.parentId);
        m.put("complete", r -> r.complete == null ? null : r.complete.toString());
        m.put("expired", r -> r.expired == null ? null : r.expired.toString());
        m.put("continuous", r -> r.continuous == null ? null : r.continuous.toString());
        m.put("electronically_signed", r -> r.electronicallySigned == null ? null : r.electronicallySigned.toString());
        m.put("signed", r -> iso(r.signed));
        m.put("finished", r -> iso(r.finished));
        m.put("activated", r -> iso(r.activated));
        m.put("expiration", r -> iso(r.expiration));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("total_items", r -> r.totalItems == null ? null : String.valueOf(r.totalItems));
        m.put("completed_items", r -> r.completedItems == null ? null : String.valueOf(r.completedItems));
        m.put("open_items", r -> r.openItems == null ? null : String.valueOf(r.openItems));
        m.put("total_entities", r -> r.totalEntities == null ? null : String.valueOf(r.totalEntities));
        m.put("completed_entities", r -> r.completedEntities == null ? null : String.valueOf(r.completedEntities));
        m.put("open_entities", r -> r.openEntities == null ? null : String.valueOf(r.openEntities));
        m.put("percent_complete", r -> r.percentComplete == null ? null : String.valueOf(r.percentComplete));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("approver_rule", r -> r.approverRule);
        m.put("automatic_closing_date", r -> r.automaticClosingDate);
        return m;
    }

    /** Re-hydrate a pre-serialized JSON string into a JSON value/object/array; null/empty stays null. */
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
    private static final class CollectingSink implements NativeCertificationSink {
        final List<NativeCertificationRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeCertificationRepository.UpsertOutcome upsert(NativeCertificationRecord record) {
            records.add(record);
            return NativeCertificationRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
