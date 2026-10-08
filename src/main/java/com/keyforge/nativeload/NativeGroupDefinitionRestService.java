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
 * KF Agent REST read service for native group definitions ({@code sailpoint.object.GroupDefinition} —
 * Populations and Groups — → {@code kf_group_definition}). Reuses the EXISTING native extraction verbatim: the
 * same {@link NativeGroupDefinitionClient} page source + {@link NativeGroupDefinitionImportService} that the DB
 * path uses, with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No
 * existing extraction or DB code is modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified field-by-field against {@link NativeGroupDefinitionRepository}'s upsert
 * bindings — every non-lineage column binds a direct native {@link NativeGroupDefinitionRecord} value, with no
 * KeyForge-derived canonical/join columns): 15 SailPoint-facing fields, all scalar (no structured/jsonb).
 * {@code is_private}/{@code indexed}/{@code null_group}/{@code name_unique} are booleans;
 * {@code last_refresh}/{@code created_at}/{@code modified_at} are ISO-8601 timestamps; every other field is
 * text ({@code factory_id} is the native GroupFactory id — a source value, not a KeyForge UUID). All 15 are
 * filterable. The only excluded columns are genuinely technical: the KeyForge {@code groupid} PK (a canonical
 * UUID), {@code record_hash}, and the lineage envelope ({@code source_system}/{@code source_interface}/
 * {@code source_object_type}/{@code extraction_run_id}/{@code extracted_at}). No native {@code modifiedAfter}
 * (the client has no server-side modified filter → full-scan only).
 */
public final class NativeGroupDefinitionRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    /** Response field name -> value extractor (all 15 scalar, all filterable). */
    private static final Map<String, Function<NativeGroupDefinitionRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeGroupDefinitionPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeGroupDefinitionRecord> all = collectAll(source);

        List<NativeGroupDefinitionRecord> matched = new ArrayList<>();
        for (NativeGroupDefinitionRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeGroupDefinitionRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeGroupDefinitionRecord r, Map<String, String> filters) {
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
    private List<NativeGroupDefinitionRecord> collectAll(NativeGroupDefinitionPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeGroupDefinitionImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native group-definition extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_group_definition order). */
    private Map<String, Object> toJson(NativeGroupDefinitionRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("type", r.type);
        m.put("factory_id", r.factoryId);
        m.put("factory_name", r.factoryName);
        m.put("filter_expression", r.filterExpression);
        m.put("is_private", r.isPrivate);                // boolean
        m.put("indexed", r.indexed);                     // boolean
        m.put("null_group", r.nullGroup);                // boolean
        m.put("name_unique", r.nameUnique);              // boolean
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("last_refresh", iso(r.lastRefresh));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeGroupDefinitionRecord, String>> buildScalars() {
        Map<String, Function<NativeGroupDefinitionRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("type", r -> r.type);
        m.put("factory_id", r -> r.factoryId);
        m.put("factory_name", r -> r.factoryName);
        m.put("filter_expression", r -> r.filterExpression);
        m.put("is_private", r -> str(r.isPrivate));
        m.put("indexed", r -> str(r.indexed));
        m.put("null_group", r -> str(r.nullGroup));
        m.put("name_unique", r -> str(r.nameUnique));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("last_refresh", r -> iso(r.lastRefresh));
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

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeGroupDefinitionSink {
        final List<NativeGroupDefinitionRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeGroupDefinitionRepository.UpsertOutcome upsert(NativeGroupDefinitionRecord record) {
            records.add(record);
            return NativeGroupDefinitionRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepGroupIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
