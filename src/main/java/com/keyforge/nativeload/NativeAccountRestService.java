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
 * KF Agent REST read service for native Accounts ({@code sailpoint.object.Link}). Same architecture as
 * the Entitlement/Identity/Application REST services: it reuses the EXISTING native extraction path
 * end-to-end — the plugin REST page source ({@link NativeLinkClient}), {@link NativeLinkParser}, and the
 * {@link NativeLinkImportService} paging loop — and persists nothing (a non-JDBC {@link CollectingSink}
 * gathers the records in memory instead of writing to PostgreSQL). No existing extraction or DB code is
 * modified or deleted; the DB write is bypassed here by construction.
 *
 * <p><b>Generic filtering</b> on any scalar response field by its response/mapped name (exact,
 * AND-combined, over the full population before {@code start}/{@code limit}). The 5 jsonb fields are not
 * filterable. The KeyForge {@code accountid} PK, {@code record_hash}, {@code is_deleted}/{@code deleted_at}
 * (not in the native record) and the lineage envelope are excluded. Link has no {@code source_hash}.
 */
public final class NativeAccountRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The 5 jsonb fields are intentionally NOT filterable. */
    private static final Map<String, Function<NativeLinkRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize. {@code source} is injected so the caller decides the
     * server-side {@code modifiedAfter} bound (and so this is unit-testable with a canned page source).
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeLinkPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeLinkRecord> all = collectAll(source);

        List<NativeLinkRecord> matched = new ArrayList<>();
        for (NativeLinkRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeLinkRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeLinkRecord r, Map<String, String> filters) {
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
    private List<NativeLinkRecord> collectAll(NativeLinkPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeLinkImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native account extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Account fields, keyed by our DB column names (kf_account order). */
    private Map<String, Object> toJson(NativeLinkRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("link_uuid", r.uuid);
        m.put("native_identity", r.nativeIdentity);
        m.put("display_name", r.displayName);
        m.put("displayable_name", r.displayableName);
        m.put("instance", r.instance);
        m.put("component_ids", r.componentIds);
        m.put("application_id", r.applicationId);
        m.put("application_name", r.applicationName);
        m.put("identity_id", r.identityId);
        m.put("identity_name", r.identityName);
        m.put("disabled", r.disabled);
        m.put("locked", r.locked);
        m.put("composite", r.composite);
        m.put("manually_correlated", r.manuallyCorrelated);
        m.put("has_entitlements", r.hasEntitlements);
        m.put("iiq_disabled", r.iiqDisabled);
        m.put("iiq_locked", r.iiqLocked);
        m.put("permissions", node(r.permissionsJson));
        m.put("target_permissions", node(r.targetPermissionsJson));
        m.put("attributes", node(r.attributesJson));
        m.put("entitlement_attributes", node(r.entitlementAttributesJson));
        m.put("attribute_metadata", node(r.attributeMetadataJson));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("last_refresh", iso(r.lastRefresh));
        m.put("last_target_aggregation", iso(r.lastTargetAggregation));
        m.put("significant_modified", iso(r.significantModified));
        m.put("prior_significant_modified", iso(r.priorSignificantModified));
        return m;
    }

    private static Map<String, Function<NativeLinkRecord, String>> buildScalars() {
        Map<String, Function<NativeLinkRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("link_uuid", r -> r.uuid);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("display_name", r -> r.displayName);
        m.put("displayable_name", r -> r.displayableName);
        m.put("instance", r -> r.instance);
        m.put("component_ids", r -> r.componentIds);
        m.put("application_id", r -> r.applicationId);
        m.put("application_name", r -> r.applicationName);
        m.put("identity_id", r -> r.identityId);
        m.put("identity_name", r -> r.identityName);
        m.put("disabled", r -> str(r.disabled));
        m.put("locked", r -> str(r.locked));
        m.put("composite", r -> str(r.composite));
        m.put("manually_correlated", r -> str(r.manuallyCorrelated));
        m.put("has_entitlements", r -> str(r.hasEntitlements));
        m.put("iiq_disabled", r -> str(r.iiqDisabled));
        m.put("iiq_locked", r -> str(r.iiqLocked));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("last_refresh", r -> iso(r.lastRefresh));
        m.put("last_target_aggregation", r -> iso(r.lastTargetAggregation));
        m.put("significant_modified", r -> iso(r.significantModified));
        m.put("prior_significant_modified", r -> iso(r.priorSignificantModified));
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
    private static final class CollectingSink implements NativeLinkSink {
        final List<NativeLinkRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeLinkRepository.UpsertOutcome upsert(NativeLinkRecord record) {
            records.add(record);
            return NativeLinkRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepAccountIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
