package com.keyforge.nativeload;

import com.keyforge.iiq.deletion.SoftDeleteSweeper;

import java.sql.SQLException;
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
 * KF Agent REST read service for native account-entitlement edges (one row per account↔entitlement-value
 * on a Link). Same architecture as the other KF Agent REST services: it reuses the EXISTING native
 * extraction path end-to-end — the plugin REST page source ({@link NativeAccountEntitlementClient}),
 * {@link NativeAccountEntitlementParser}, and the {@link NativeAccountEntitlementImportService} paging
 * loop — and persists nothing (a non-JDBC {@link CollectingSink} gathers the records in memory instead of
 * writing to PostgreSQL). No existing extraction or DB code is modified or deleted; the DB write is
 * bypassed by construction.
 *
 * <p>Per the actual native record / {@code kf_account_entitlement} schema (verified): every field is a
 * scalar string — there are no nested/jsonb fields and no timestamps. All 9 fields are filterable. The
 * KeyForge {@code accountentitlementid} PK, {@code record_hash}, {@code is_deleted}/{@code deleted_at}
 * (not in the native record) and the lineage envelope are excluded. This edge has no {@code source_hash},
 * and the native client has no server-side {@code modifiedAfter} support (full-scan only).
 */
public final class NativeAccountEntitlementRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    /** Response field name -> value extractor (all scalar, all filterable). */
    private static final Map<String, Function<NativeAccountEntitlementRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeAccountEntitlementPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeAccountEntitlementRecord> all = collectAll(source);

        List<NativeAccountEntitlementRecord> matched = new ArrayList<>();
        for (NativeAccountEntitlementRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeAccountEntitlementRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeAccountEntitlementRecord r, Map<String, String> filters) {
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
    private List<NativeAccountEntitlementRecord> collectAll(NativeAccountEntitlementPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeAccountEntitlementImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native account-entitlement extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_account_entitlement order). */
    private Map<String, Object> toJson(NativeAccountEntitlementRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("link_id", r.linkId);
        m.put("identity_id", r.identityId);
        m.put("identity_name", r.identityName);
        m.put("application_id", r.applicationId);
        m.put("application_name", r.applicationName);
        m.put("native_identity", r.nativeIdentity);
        m.put("instance", r.instance);
        m.put("attribute_name", r.attributeName);
        m.put("attribute_value", r.attributeValue);
        return m;
    }

    private static Map<String, Function<NativeAccountEntitlementRecord, String>> buildScalars() {
        Map<String, Function<NativeAccountEntitlementRecord, String>> m = new LinkedHashMap<>();
        m.put("link_id", r -> r.linkId);
        m.put("identity_id", r -> r.identityId);
        m.put("identity_name", r -> r.identityName);
        m.put("application_id", r -> r.applicationId);
        m.put("application_name", r -> r.applicationName);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("instance", r -> r.instance);
        m.put("attribute_name", r -> r.attributeName);
        m.put("attribute_value", r -> r.attributeValue);
        return m;
    }

    /** In-memory sink: reuses the import loop but persists nothing (DB bypass for the REST path). */
    private static final class CollectingSink implements NativeAccountEntitlementSink {
        final List<NativeAccountEntitlementRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeAccountEntitlementRepository.UpsertOutcome upsert(NativeAccountEntitlementRecord record) {
            records.add(record);
            return NativeAccountEntitlementRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
