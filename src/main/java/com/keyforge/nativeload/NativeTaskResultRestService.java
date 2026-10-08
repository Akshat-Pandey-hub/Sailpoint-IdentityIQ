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
 * KF Agent REST read service for native task results ({@code sailpoint.object.TaskResult} →
 * {@code kf_task_result}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativeTaskResultClient} page source + {@link NativeTaskResultImportService} that the DB path uses,
 * with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No existing
 * extraction or DB code is modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeTaskResultRecord} + {@link NativeTaskResultParser} +
 * {@code kf_task_result}): 26 SailPoint-facing fields. {@code messages} and {@code attributes} are the
 * structured ({@code jsonb}) fields — returned as JSON, not filterable. Verified native types (NOT the DB
 * display): {@code progress} is TEXT; {@code percent_complete}/{@code run_length}/{@code pending_signoffs}
 * are INTEGERS; {@code partitioned}/{@code terminate_requested}/{@code complete} are BOOLEANS;
 * {@code launched_at}/{@code completed_at}/{@code expiration_at}/{@code verified_at}/{@code created_at}/
 * {@code modified_at} are ISO-8601 timestamps; every other field is text. The 24 scalar fields are
 * filterable. The KeyForge {@code taskresultid} PK, {@code record_hash}, lineage envelope and soft-delete
 * columns are excluded. No native {@code modifiedAfter} (the client has no server-side modified filter →
 * full-scan only).
 */
public final class NativeTaskResultRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The two jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativeTaskResultRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeTaskResultPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeTaskResultRecord> all = collectAll(source);

        List<NativeTaskResultRecord> matched = new ArrayList<>();
        for (NativeTaskResultRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeTaskResultRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeTaskResultRecord r, Map<String, String> filters) {
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

    /** Walks the native source to completion via the EXISTING import; collecting sink = no DB. */
    private List<NativeTaskResultRecord> collectAll(NativeTaskResultPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeTaskResultImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native task-result extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_task_result order). */
    private Map<String, Object> toJson(NativeTaskResultRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("type", r.type);
        m.put("completion_status", r.completionStatus);
        m.put("definition_name", r.definitionName);
        m.put("launcher", r.launcher);
        m.put("host", r.host);
        m.put("target_name", r.targetName);
        m.put("target_class", r.targetClass);
        m.put("target_id", r.targetId);
        m.put("schedule", r.schedule);
        m.put("progress", r.progress);                            // text
        m.put("percent_complete", r.percentComplete);             // integer
        m.put("run_length", r.runLength);                         // integer
        m.put("pending_signoffs", r.pendingSignoffs);             // integer
        m.put("partitioned", r.partitioned);                      // boolean
        m.put("terminate_requested", r.terminateRequested);       // boolean
        m.put("complete", r.complete);                            // boolean
        m.put("launched_at", iso(r.launched));
        m.put("completed_at", iso(r.completed));
        m.put("expiration_at", iso(r.expiration));
        m.put("verified_at", iso(r.verified));
        m.put("messages", node(r.messagesJson));                  // jsonb
        m.put("attributes", node(r.attributesJson));              // jsonb
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeTaskResultRecord, String>> buildScalars() {
        Map<String, Function<NativeTaskResultRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("type", r -> r.type);
        m.put("completion_status", r -> r.completionStatus);
        m.put("definition_name", r -> r.definitionName);
        m.put("launcher", r -> r.launcher);
        m.put("host", r -> r.host);
        m.put("target_name", r -> r.targetName);
        m.put("target_class", r -> r.targetClass);
        m.put("target_id", r -> r.targetId);
        m.put("schedule", r -> r.schedule);
        m.put("progress", r -> r.progress);
        m.put("percent_complete", r -> r.percentComplete == null ? null : String.valueOf(r.percentComplete));
        m.put("run_length", r -> r.runLength == null ? null : String.valueOf(r.runLength));
        m.put("pending_signoffs", r -> r.pendingSignoffs == null ? null : String.valueOf(r.pendingSignoffs));
        m.put("partitioned", r -> str(r.partitioned));
        m.put("terminate_requested", r -> str(r.terminateRequested));
        m.put("complete", r -> str(r.complete));
        m.put("launched_at", r -> iso(r.launched));
        m.put("completed_at", r -> iso(r.completed));
        m.put("expiration_at", r -> iso(r.expiration));
        m.put("verified_at", r -> iso(r.verified));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        return m;
    }

    /** Re-hydrate a pre-serialized jsonb string into a JSON value/array/object; null/empty stays null. */
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

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeTaskResultSink {
        final List<NativeTaskResultRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeTaskResultRepository.UpsertOutcome upsert(NativeTaskResultRecord record) {
            records.add(record);
            return NativeTaskResultRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepTaskResultIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
