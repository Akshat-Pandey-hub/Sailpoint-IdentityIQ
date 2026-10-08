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
 * KF Agent REST read service for native IdentityEntitlements ({@code sailpoint.object.IdentityEntitlement}
 * — one row per identity↔entitlement assignment). Same architecture as the other KF Agent REST services:
 * it reuses the EXISTING native extraction path end-to-end — the plugin REST page source
 * ({@link NativeIdentityEntitlementClient}), {@link NativeIdentityEntitlementParser}, and the
 * {@link NativeIdentityEntitlementImportService} paging loop — and persists nothing (a non-JDBC
 * {@link CollectingSink} gathers the records in memory instead of writing to PostgreSQL). No existing
 * extraction or DB code is modified or deleted; the DB write is bypassed by construction.
 *
 * <p><b>Field types per the actual native mapping / {@code kf_identity_entitlement} schema</b> (verified):
 * {@code value_list} is the only {@code jsonb} field (returned as JSON). {@code source_assignable_roles}
 * and {@code source_detected_roles} are {@code text} columns (parser reads them with {@code text()}),
 * returned as strings. The jsonb field is not filterable; every scalar/text field is. The KeyForge
 * {@code entitlementid} PK, {@code record_hash}, {@code is_deleted}/{@code deleted_at} (not in the native
 * record) and the lineage envelope are excluded. This row has no {@code source_hash}, and the native
 * client has no server-side {@code modifiedAfter} support (full-scan only).
 */
public final class NativeIdentityEntitlementRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The {@code value_list} jsonb field is NOT filterable. */
    private static final Map<String, Function<NativeIdentityEntitlementRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeIdentityEntitlementPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeIdentityEntitlementRecord> all = collectAll(source);

        List<NativeIdentityEntitlementRecord> matched = new ArrayList<>();
        for (NativeIdentityEntitlementRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeIdentityEntitlementRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeIdentityEntitlementRecord r, Map<String, String> filters) {
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
    private List<NativeIdentityEntitlementRecord> collectAll(NativeIdentityEntitlementPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeIdentityEntitlementImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native identity-entitlement extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_identity_entitlement order). */
    private Map<String, Object> toJson(NativeIdentityEntitlementRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("identity_id", r.identityId);
        m.put("identity_name", r.identityName);
        m.put("application_id", r.applicationId);
        m.put("application_name", r.applicationName);
        m.put("native_identity", r.nativeIdentity);
        m.put("instance", r.instance);
        m.put("attribute_name", r.attributeName);
        m.put("attribute_value", r.attributeValue);
        m.put("value_list", node(r.valueListJson));   // jsonb
        m.put("type", r.type);
        m.put("display_name", r.displayName);
        m.put("annotation", r.annotation);
        m.put("assigned", r.assigned);
        m.put("granted_by_role", r.grantedByRole);
        m.put("allowed", r.allowed);
        m.put("connected", r.connected);
        m.put("aggregation_state", r.aggregationState);
        m.put("source", r.source);
        m.put("source_object", r.sourceObject);
        m.put("assigner", r.assigner);
        m.put("assignment_id", r.assignmentId);
        m.put("assignment_note", r.assignmentNote);
        m.put("source_assignable_roles", r.sourceAssignableRoles);   // text
        m.put("source_detected_roles", r.sourceDetectedRoles);       // text
        m.put("certification_item_id", r.certificationItemId);
        m.put("pending_certification_item_id", r.pendingCertificationItemId);
        m.put("request_item_id", r.requestItemId);
        m.put("pending_request_item_id", r.pendingRequestItemId);
        m.put("start_date", iso(r.startDate));
        m.put("end_date", iso(r.endDate));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeIdentityEntitlementRecord, String>> buildScalars() {
        Map<String, Function<NativeIdentityEntitlementRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("identity_id", r -> r.identityId);
        m.put("identity_name", r -> r.identityName);
        m.put("application_id", r -> r.applicationId);
        m.put("application_name", r -> r.applicationName);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("instance", r -> r.instance);
        m.put("attribute_name", r -> r.attributeName);
        m.put("attribute_value", r -> r.attributeValue);
        m.put("type", r -> r.type);
        m.put("display_name", r -> r.displayName);
        m.put("annotation", r -> r.annotation);
        m.put("assigned", r -> str(r.assigned));
        m.put("granted_by_role", r -> str(r.grantedByRole));
        m.put("allowed", r -> str(r.allowed));
        m.put("connected", r -> str(r.connected));
        m.put("aggregation_state", r -> r.aggregationState);
        m.put("source", r -> r.source);
        m.put("source_object", r -> r.sourceObject);
        m.put("assigner", r -> r.assigner);
        m.put("assignment_id", r -> r.assignmentId);
        m.put("assignment_note", r -> r.assignmentNote);
        m.put("source_assignable_roles", r -> r.sourceAssignableRoles);
        m.put("source_detected_roles", r -> r.sourceDetectedRoles);
        m.put("certification_item_id", r -> r.certificationItemId);
        m.put("pending_certification_item_id", r -> r.pendingCertificationItemId);
        m.put("request_item_id", r -> r.requestItemId);
        m.put("pending_request_item_id", r -> r.pendingRequestItemId);
        m.put("start_date", r -> iso(r.startDate));
        m.put("end_date", r -> iso(r.endDate));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
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

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeIdentityEntitlementSink {
        final List<NativeIdentityEntitlementRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeIdentityEntitlementRepository.UpsertOutcome upsert(NativeIdentityEntitlementRecord record) {
            records.add(record);
            return NativeIdentityEntitlementRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
