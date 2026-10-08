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
 * KF Agent REST read service for native policy violations ({@code sailpoint.object.PolicyViolation} →
 * {@code kf_violation}). Reuses the EXISTING native extraction verbatim: the same {@link NativeViolationClient}
 * page source + {@link NativeViolationImportService} that the DB path uses, with a non-JDBC
 * {@link CollectingSink} that collects the records in memory (DB bypass). No existing extraction or DB code is
 * modified; no PostgreSQL is touched on this path.
 *
 * <p><b>REST contract</b> (verified field-by-field against {@link NativeViolationRepository}'s upsert
 * bindings): 19 SailPoint-facing fields. {@code relevant_apps}, {@code violating_entitlements} and
 * {@code arguments} are the structured ({@code jsonb}) fields — returned as JSON, not filterable.
 * {@code active} is a boolean; {@code created_at}/{@code modified_at} are ISO-8601 timestamps;
 * {@code left_bundles}/{@code right_bundles}/{@code entitlements_marked_for_remediation}/
 * {@code bundles_marked_for_remediation} are {@code text} columns (the SoD left/right role sets and the
 * remediation markings, read with {@code text()}), returned as strings; every other field is text. The 16
 * scalar fields are filterable. {@code identity_id}/{@code policy_id}/{@code constraint_id} are the native
 * SailPoint ids (bound directly from the record — not KeyForge canonical UUIDs). Excluded as technical: the
 * KeyForge {@code violationid} PK (a canonical UUID), {@code record_hash}, and the lineage envelope
 * ({@code source_system}/{@code source_interface}/{@code source_object_type}/{@code extraction_run_id}/
 * {@code extracted_at}). No native {@code modifiedAfter} (the client has no server-side modified filter →
 * full-scan only). The preview instance currently has 0 policy violations, so the live response is {@code []}
 * until a violation exists.
 */
public final class NativeViolationRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The three jsonb fields are NOT filterable. */
    private static final Map<String, Function<NativeViolationRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeViolationPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeViolationRecord> all = collectAll(source);

        List<NativeViolationRecord> matched = new ArrayList<>();
        for (NativeViolationRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeViolationRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeViolationRecord r, Map<String, String> filters) {
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
    private List<NativeViolationRecord> collectAll(NativeViolationPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeViolationImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native policy-violation extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_violation order). */
    private Map<String, Object> toJson(NativeViolationRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("identity_id", r.identityId);
        m.put("identity_name", r.identityName);
        m.put("policy_id", r.policyId);
        m.put("policy_name", r.policyName);
        m.put("constraint_id", r.constraintId);
        m.put("constraint_name", r.constraintName);
        m.put("status", r.status);
        m.put("active", r.active);                                                   // boolean
        m.put("left_bundles", r.leftBundles);                                        // text (SoD left roles)
        m.put("right_bundles", r.rightBundles);                                      // text (SoD right roles)
        m.put("entitlements_marked_for_remediation", r.entitlementsMarkedForRemediation);  // text
        m.put("bundles_marked_for_remediation", r.bundlesMarkedForRemediation);      // text
        m.put("relevant_apps", node(r.relevantAppsJson));                            // jsonb
        m.put("violating_entitlements", node(r.violatingEntitlementsJson));          // jsonb
        m.put("arguments", node(r.argumentsJson));                                   // jsonb
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeViolationRecord, String>> buildScalars() {
        Map<String, Function<NativeViolationRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("identity_id", r -> r.identityId);
        m.put("identity_name", r -> r.identityName);
        m.put("policy_id", r -> r.policyId);
        m.put("policy_name", r -> r.policyName);
        m.put("constraint_id", r -> r.constraintId);
        m.put("constraint_name", r -> r.constraintName);
        m.put("status", r -> r.status);
        m.put("active", r -> r.active == null ? null : r.active.toString());
        m.put("left_bundles", r -> r.leftBundles);
        m.put("right_bundles", r -> r.rightBundles);
        m.put("entitlements_marked_for_remediation", r -> r.entitlementsMarkedForRemediation);
        m.put("bundles_marked_for_remediation", r -> r.bundlesMarkedForRemediation);
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
    private static final class CollectingSink implements NativeViolationSink {
        final List<NativeViolationRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeViolationRepository.UpsertOutcome upsert(NativeViolationRecord record) {
            records.add(record);
            return NativeViolationRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepViolationIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
