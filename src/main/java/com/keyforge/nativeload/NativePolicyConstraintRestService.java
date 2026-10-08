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
 * KF Agent REST read service for native policy constraints ({@code sailpoint.object.BaseConstraint} →
 * {@code kf_policy_constraint}). Reuses the EXISTING native extraction verbatim: the same
 * {@link NativePolicyConstraintClient} page source + {@link NativePolicyConstraintImportService} that the DB
 * path uses, with a non-JDBC {@link CollectingSink} that collects the records in memory (DB bypass). No
 * existing extraction or DB code is modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified from {@link NativePolicyConstraintRecord} +
 * {@link NativePolicyConstraintParser} + {@code kf_policy_constraint}): 18 SailPoint-facing fields.
 * {@code left_bundles}, {@code right_bundles}, {@code selectors} and {@code arguments} are the structured
 * ({@code jsonb}) fields — returned as JSON, not filterable. {@code weight} and {@code selector_count} are
 * integers; {@code created_at}/{@code modified_at} are ISO-8601 timestamps; every other field is text. The
 * 14 scalar fields are filterable. The KeyForge {@code policyconstraintid} PK, {@code record_hash}, lineage
 * envelope and soft-delete columns are excluded. No native {@code modifiedAfter} (the client has no
 * server-side modified filter → full-scan only).
 */
public final class NativePolicyConstraintRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The four jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativePolicyConstraintRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativePolicyConstraintPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativePolicyConstraintRecord> all = collectAll(source);

        List<NativePolicyConstraintRecord> matched = new ArrayList<>();
        for (NativePolicyConstraintRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativePolicyConstraintRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativePolicyConstraintRecord r, Map<String, String> filters) {
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
    private List<NativePolicyConstraintRecord> collectAll(NativePolicyConstraintPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativePolicyConstraintImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native policy-constraint extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_policy_constraint order). */
    private Map<String, Object> toJson(NativePolicyConstraintRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("policy_id", r.policyId);
        m.put("policy_name", r.policyName);
        m.put("name", r.name);
        m.put("description", r.description);
        m.put("constraint_type", r.constraintType);
        m.put("weight", r.weight);                               // integer
        m.put("compensating_control", r.compensatingControl);
        m.put("violation_owner_id", r.violationOwnerId);
        m.put("violation_owner_name", r.violationOwnerName);
        m.put("violation_owner_type", r.violationOwnerType);
        m.put("left_bundles", node(r.leftBundlesJson));          // jsonb
        m.put("right_bundles", node(r.rightBundlesJson));        // jsonb
        m.put("selectors", node(r.selectorsJson));               // jsonb
        m.put("selector_count", r.selectorCount);                // integer
        m.put("arguments", node(r.argumentsJson));               // jsonb
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativePolicyConstraintRecord, String>> buildScalars() {
        Map<String, Function<NativePolicyConstraintRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("policy_id", r -> r.policyId);
        m.put("policy_name", r -> r.policyName);
        m.put("name", r -> r.name);
        m.put("description", r -> r.description);
        m.put("constraint_type", r -> r.constraintType);
        m.put("weight", r -> r.weight == null ? null : String.valueOf(r.weight));
        m.put("compensating_control", r -> r.compensatingControl);
        m.put("violation_owner_id", r -> r.violationOwnerId);
        m.put("violation_owner_name", r -> r.violationOwnerName);
        m.put("violation_owner_type", r -> r.violationOwnerType);
        m.put("selector_count", r -> r.selectorCount == null ? null : String.valueOf(r.selectorCount));
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

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativePolicyConstraintSink {
        final List<NativePolicyConstraintRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativePolicyConstraintRepository.UpsertOutcome upsert(NativePolicyConstraintRecord record) {
            records.add(record);
            return NativePolicyConstraintRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepConstraintIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
