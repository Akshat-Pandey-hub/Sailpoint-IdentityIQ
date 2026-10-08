package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KF Agent REST read service for native workflow definitions ({@code sailpoint.object.Workflow} →
 * {@code kf_workflow_definition}). Reuses the EXISTING native extraction + mapping verbatim: the same
 * {@link NativeWorkflowClient} transport and {@link NativeWorkflowParser} the DB path uses. Unlike most
 * entities, {@link NativeWorkflowImportService} has no sink seam — it is coupled directly to the JDBC
 * {@link NativeWorkflowRepository} and needs a {@link java.sql.Connection} — so the REST path cannot reuse the
 * import service without touching PostgreSQL. It therefore replays the SAME page→parse loop (client + parser)
 * into an in-memory list, with no repository, no connection and no upsert/sweep. No existing extraction or DB
 * code is modified.
 *
 * <p><b>REST contract</b> (verified from {@link NativeWorkflowRecord} + {@link NativeWorkflowParser} +
 * {@code kf_workflow_definition}): 9 SailPoint-facing fields. {@code definition} is the only structured field
 * — the parser stores it as a pre-serialized JSON string ({@code node.toString()}), re-hydrated here to real
 * JSON; {@code created_at}/{@code modified_at} are ISO-8601 timestamps; every other field is {@code text}. The
 * {@code definition} field is not filterable; the other 8 are scalar and filterable. The KeyForge
 * {@code workflowid} PK, {@code record_hash}, lineage envelope and soft-delete columns are excluded. No native
 * {@code modifiedAfter} (the workflow client has no server-side modified filter → full-scan only).
 */
public final class NativeWorkflowDefinitionRestService {

    /** Transport seam: {@code NativeWorkflowClient::fetch} in production, a fake in tests (no live IIQ, no DB). */
    @FunctionalInterface
    public interface PageSource {
        String fetchPage(int start, int limit);
    }

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();
    private final NativeWorkflowParser parser = new NativeWorkflowParser();

    /** Scalar response field name -> value extractor. The {@code definition} JSON field is NOT filterable. */
    private static final Map<String, Function<NativeWorkflowRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(PageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeWorkflowRecord> all = collectAll(source);

        List<NativeWorkflowRecord> matched = new ArrayList<>();
        for (NativeWorkflowRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeWorkflowRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeWorkflowRecord r, Map<String, String> filters) {
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

    /**
     * Walks the native source to completion with the SAME page→parse loop the import service uses, minus the
     * JDBC upsert/sweep (DB bypass): page → {@link NativeWorkflowParser#parse} → collect.
     */
    private List<NativeWorkflowRecord> collectAll(PageSource source) {
        List<NativeWorkflowRecord> all = new ArrayList<>();
        int start = 0;
        while (true) {
            List<NativeWorkflowRecord> page = parser.parse(source.fetchPage(start, INTERNAL_PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < INTERNAL_PAGE_SIZE) {
                break;
            }
            start += INTERNAL_PAGE_SIZE;
        }
        return all;
    }

    /** The SailPoint-facing workflow fields, keyed by our DB column names (kf_workflow_definition order). */
    private Map<String, Object> toJson(NativeWorkflowRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("type", r.type);
        m.put("handler", r.handler);
        m.put("description", r.description);
        m.put("task_type", r.taskType);
        m.put("definition", node(r.definition));   // structured JSON (never stringified)
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeWorkflowRecord, String>> buildScalars() {
        Map<String, Function<NativeWorkflowRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("type", r -> r.type);
        m.put("handler", r -> r.handler);
        m.put("description", r -> r.description);
        m.put("task_type", r -> r.taskType);
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
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
}
