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
 * KF Agent REST read service for native Policies ({@code sailpoint.object.Policy} → {@code kf_policy}).
 * Same architecture as the other KF Agent REST services: it reuses the EXISTING native extraction path
 * end-to-end — the plugin REST page source ({@link NativePolicyClient}), {@link NativePolicyParser}, and
 * the {@link NativePolicyImportService} paging loop — and persists nothing (a non-JDBC
 * {@link CollectingSink} gathers the records in memory instead of writing to PostgreSQL). No existing
 * extraction or DB code is modified or deleted; the DB write is bypassed by construction.
 *
 * <p><b>Field types per the actual native mapping / {@code kf_policy} schema</b> (verified, not assumed
 * from the DB display): {@code descriptions} is the only {@code jsonb} field (returned as JSON). Despite
 * looking structured, {@code violation_rule}, {@code violation_workflow}, {@code signature} and
 * {@code certification_actions} are {@code text} columns (parser reads them with {@code text()}; e.g.
 * {@code certification_actions} is a comma-joined string), so they are returned as strings. The jsonb
 * field is not filterable; the other 16 are scalar ({@code constraint_count} is an integer) and filterable.
 * The KeyForge {@code policyid} PK, {@code record_hash}, {@code is_deleted}/{@code deleted_at} (not in the
 * native record) and the lineage envelope are excluded. No {@code source_hash}; no native
 * {@code modifiedAfter} (full-scan only).
 */
public final class NativePolicyRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The {@code descriptions} jsonb field is NOT filterable. */
    private static final Map<String, Function<NativePolicyRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativePolicyPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativePolicyRecord> all = collectAll(source);

        List<NativePolicyRecord> matched = new ArrayList<>();
        for (NativePolicyRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativePolicyRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativePolicyRecord r, Map<String, String> filters) {
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
    private List<NativePolicyRecord> collectAll(NativePolicyPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativePolicyImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native policy extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Policy fields, keyed by our DB column names. */
    private Map<String, Object> toJson(NativePolicyRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("type", r.type);
        m.put("type_key", r.typeKey);
        m.put("description", r.description);
        m.put("descriptions", node(r.descriptionsJson));   // jsonb
        m.put("executor", r.executor);
        m.put("violation_owner_id", r.violationOwnerId);
        m.put("violation_owner_name", r.violationOwnerName);
        m.put("constraint_count", r.constraintCount);
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("state", r.state);
        m.put("violation_rule", r.violationRule);                 // text
        m.put("violation_workflow", r.violationWorkflow);         // text
        m.put("signature", r.signature);                         // text
        m.put("certification_actions", r.certificationActions);   // text
        return m;
    }

    private static Map<String, Function<NativePolicyRecord, String>> buildScalars() {
        Map<String, Function<NativePolicyRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("type", r -> r.type);
        m.put("type_key", r -> r.typeKey);
        m.put("description", r -> r.description);
        m.put("executor", r -> r.executor);
        m.put("violation_owner_id", r -> r.violationOwnerId);
        m.put("violation_owner_name", r -> r.violationOwnerName);
        m.put("constraint_count", r -> r.constraintCount == null ? null : String.valueOf(r.constraintCount));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("state", r -> r.state);
        m.put("violation_rule", r -> r.violationRule);
        m.put("violation_workflow", r -> r.violationWorkflow);
        m.put("signature", r -> r.signature);
        m.put("certification_actions", r -> r.certificationActions);
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

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativePolicySink {
        final List<NativePolicyRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativePolicyRepository.UpsertOutcome upsert(NativePolicyRecord record) {
            records.add(record);
            return NativePolicyRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepPolicyIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
