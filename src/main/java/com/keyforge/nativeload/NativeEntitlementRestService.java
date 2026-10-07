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
 * KF Agent REST read service for native Entitlements ({@code ManagedAttribute}). Reuses the EXISTING
 * native extraction path end-to-end — the plugin REST page source, {@link NativeManagedAttributeParser},
 * and the {@link NativeManagedAttributeImportService} paging loop — and persists nothing: a non-JDBC
 * {@link CollectingSink} gathers the records in memory instead of writing to PostgreSQL. No existing
 * extraction or DB code is modified or deleted; the DB write is bypassed here by construction.
 *
 * <p><b>Generic filtering.</b> Any scalar response field can be used as a query filter under its own
 * name (our DB/response field name, e.g. {@code source_id}, {@code value}, {@code type},
 * {@code application_name}, {@code is_group_type}). Multiple filters AND together, each an exact match.
 * Filtering runs over the COMPLETE population (never a single native page), then the {@code start}/
 * {@code limit} output window is applied, then rows are serialized to the 43 SailPoint-facing fields
 * (KeyForge PK, record_hash and lineage/ingestion metadata are excluded — they are not SailPoint data).
 */
public final class NativeEntitlementRestService {

    /** Page size used internally to walk the native source to completion (separate from the output window). */
    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. Order mirrors the response. Nested jsonb fields are
     *  intentionally NOT filterable (exact-matching a JSON object/array by string is meaningless). */
    private static final Map<String, Function<NativeManagedAttributeRecord, String>> SCALARS = buildScalars();

    /** The field names a caller may filter on (the scalar subset of the 43 response fields). */
    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize. {@code source} is injected so the caller decides the
     * server-side {@code modifiedAfter} bound (and so this is unit-testable with a canned page source).
     *
     * @param filters response-field-name → exact value (AND-combined); keys must be in {@link #FILTERABLE_FIELDS}
     * @param start   output offset (0-based; null/negative = 0)
     * @param limit   output page size (null/negative = return all remaining)
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeManagedAttributePageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeManagedAttributeRecord> all = collectAll(source);

        List<NativeManagedAttributeRecord> matched = new ArrayList<>();
        for (NativeManagedAttributeRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeManagedAttributeRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    /** True when every requested filter exactly matches the record's scalar value (null never matches). */
    private static boolean matches(NativeManagedAttributeRecord r, Map<String, String> filters) {
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
    private List<NativeManagedAttributeRecord> collectAll(NativeManagedAttributePageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeManagedAttributeImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native entitlement extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The 43 SailPoint-facing fields, keyed by our DB column names. Excludes KeyForge PK/hash/lineage. */
    private Map<String, Object> toJson(NativeManagedAttributeRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("value", r.value);
        m.put("display_name", r.displayName);
        m.put("displayable_name", r.displayableName);
        m.put("attribute", r.attribute);
        m.put("type", r.type);
        m.put("ma_uuid", r.uuid);
        m.put("reference_attribute", r.referenceAttribute);
        m.put("purview", r.purview);
        m.put("application_id", r.applicationId);
        m.put("application_name", r.applicationName);
        m.put("instance", r.instance);
        m.put("native_identity", r.nativeIdentity);
        m.put("requestable", r.requestable);
        m.put("is_group", r.group);
        m.put("is_permission", r.permission);
        m.put("uncorrelated", r.uncorrelated);
        m.put("aggregated", r.aggregated);
        m.put("iiq_elevated_access", r.iiqElevatedAccess);
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("description", r.description);
        m.put("descriptions", node(r.descriptionsJson));
        m.put("permissions", node(r.permissionsJson));
        m.put("target_permissions", node(r.targetPermissionsJson));
        m.put("inheritance", node(r.inheritanceJson));
        m.put("associations", node(r.associationsJson));
        m.put("attributes", node(r.attributesJson));
        m.put("classifications", node(r.classificationsJson));
        m.put("classification_names", node(r.classificationNamesJson));
        m.put("classification_display_names", node(r.classificationDisplayNamesJson));
        m.put("member_attribute", r.memberAttribute);
        m.put("full_name", r.fullName);
        m.put("is_group_type", r.groupType);
        m.put("is_inactive", r.inactive);
        m.put("is_auto_promotion", r.autoPromotion);
        m.put("is_differencable", r.differencable);
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("last_refresh", iso(r.lastRefresh));
        m.put("last_target_aggregation", iso(r.lastTargetAggregation));
        m.put("source_hash", r.sourceHash);
        return m;
    }

    private static Map<String, Function<NativeManagedAttributeRecord, String>> buildScalars() {
        Map<String, Function<NativeManagedAttributeRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("value", r -> r.value);
        m.put("display_name", r -> r.displayName);
        m.put("displayable_name", r -> r.displayableName);
        m.put("attribute", r -> r.attribute);
        m.put("type", r -> r.type);
        m.put("ma_uuid", r -> r.uuid);
        m.put("reference_attribute", r -> r.referenceAttribute);
        m.put("purview", r -> r.purview);
        m.put("application_id", r -> r.applicationId);
        m.put("application_name", r -> r.applicationName);
        m.put("instance", r -> r.instance);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("requestable", r -> str(r.requestable));
        m.put("is_group", r -> str(r.group));
        m.put("is_permission", r -> str(r.permission));
        m.put("uncorrelated", r -> str(r.uncorrelated));
        m.put("aggregated", r -> str(r.aggregated));
        m.put("iiq_elevated_access", r -> str(r.iiqElevatedAccess));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("description", r -> r.description);
        m.put("member_attribute", r -> r.memberAttribute);
        m.put("full_name", r -> r.fullName);
        m.put("is_group_type", r -> str(r.groupType));
        m.put("is_inactive", r -> str(r.inactive));
        m.put("is_auto_promotion", r -> str(r.autoPromotion));
        m.put("is_differencable", r -> str(r.differencable));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("last_refresh", r -> iso(r.lastRefresh));
        m.put("last_target_aggregation", r -> iso(r.lastTargetAggregation));
        m.put("source_hash", r -> r.sourceHash);
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
            return json; // never fail the row over a malformed nested blob
        }
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    private static String str(Boolean b) {
        return b == null ? null : b.toString();
    }

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeManagedAttributeSink {
        final List<NativeManagedAttributeRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeManagedAttributeRepository.UpsertOutcome upsert(NativeManagedAttributeRecord record) {
            records.add(record);
            return NativeManagedAttributeRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepEntitlementIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
