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
 * KF Agent REST read service for native Role-Hierarchy edges (Bundle→Bundle inheritance/requirement/permit →
 * {@code kf_role_hierarchy}). The native source is the Bundle "relationship" extraction, which produces BOTH
 * role-entitlement and role-hierarchy edges through one import; this service collects only the HIERARCHY
 * edges (the mirror of {@link NativeRoleEntitlementRestService}, which collects the entitlement edges). Same
 * architecture as the other KF Agent REST services: it reuses the EXISTING native extraction path end-to-end
 * — the plugin REST page source ({@link NativeRoleRelationshipClient}), {@link NativeRoleRelationshipParser},
 * and the {@link NativeRoleRelationshipImportService} paging loop — and persists nothing (a non-JDBC
 * {@link CollectingSink} gathers the records in memory; its two sweep methods return a skipped result so the
 * import's end-of-scan sweep is a harmless no-op). No existing extraction or DB code is modified; the DB
 * write is bypassed by construction.
 *
 * <p><b>REST contract</b> (verified from {@link NativeRoleHierarchyRecord}, the native
 * {@code NativeRoleRelationshipMapper} — {@code setSourceRoleId(source.getId())}/
 * {@code setSourceRoleName(source.getName())}/{@code setRelatedRoleId(target.getId())}/
 * {@code setRelatedRoleName(target.getName())} — and the exact {@code kf_role_hierarchy} upsert bindings):
 * 5 SailPoint-facing fields, all scalar text (no structured/jsonb, no booleans, no timestamps), named after
 * their real DB columns:
 * <ul>
 *   <li>{@code source_role_id} — {@code Bundle.getId()} of the owning role (business).</li>
 *   <li>{@code role_name} — {@code Bundle.getName()} of the owning role (DB column {@code role_name}; business).</li>
 *   <li>{@code related_role_source_id} — {@code Bundle.getId()} of the inherited/required/permitted role (DB
 *       column {@code related_role_source_id}; business).</li>
 *   <li>{@code related_role_name} — {@code Bundle.getName()} of the related role (business).</li>
 *   <li>{@code relationship_type} — INHERITANCE | REQUIREMENT | PERMIT.</li>
 * </ul>
 * All 5 are filterable. Excluded are ONLY genuinely internal/technical columns: the {@code rolehierarchyid}
 * PK and the two <b>KeyForge canonical-UUID</b> columns {@code role_id} ({@code ParquetIds} of the source
 * role) and {@code related_role_id} ({@code ParquetIds} of the related role) — both are derived join keys, NOT
 * native SailPoint data — plus {@code src_object_id}, {@code src_natural_key}, {@code record_hash},
 * {@code source_system}/{@code source_interface}/{@code source_object_type}, {@code extraction_run_id},
 * {@code extracted_at}, {@code is_deleted}, {@code deleted_at}. (No business column is omitted: every DB column
 * carrying a native {@code Bundle} value is exposed; the only omissions are the two derived canonical UUIDs.)
 * If IIQ has no role-hierarchy edges, the endpoint returns an empty array.
 */
public final class NativeRoleHierarchyRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    /** Scalar response field name -> value extractor (all 5 scalar, all filterable). */
    private static final Map<String, Function<NativeRoleHierarchyRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeRoleRelationshipPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeRoleHierarchyRecord> all = collectAll(source);

        List<NativeRoleHierarchyRecord> matched = new ArrayList<>();
        for (NativeRoleHierarchyRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeRoleHierarchyRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeRoleHierarchyRecord r, Map<String, String> filters) {
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
    private List<NativeRoleHierarchyRecord> collectAll(NativeRoleRelationshipPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // run() owns its own runId + complete-scan validation; the collecting sink persists nothing
            // and its sweeps are skipped no-ops, so no PostgreSQL is touched.
            new NativeRoleRelationshipImportService(source, sink, INTERNAL_PAGE_SIZE).run();
        } catch (SQLException e) {
            throw new NativeImportException("native role-hierarchy extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Role-Hierarchy fields, keyed by our DB business-field names. */
    private Map<String, Object> toJson(NativeRoleHierarchyRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_role_id", r.sourceRoleId);                   // Bundle.getId() of the owning role
        m.put("role_name", r.sourceRoleName);                      // Bundle.getName() of the owning role (DB col role_name)
        m.put("related_role_source_id", r.relatedRoleId);          // Bundle.getId() of the related role (DB col related_role_source_id)
        m.put("related_role_name", r.relatedRoleName);             // Bundle.getName() of the related role
        m.put("relationship_type", r.relationshipType);
        return m;
    }

    private static Map<String, Function<NativeRoleHierarchyRecord, String>> buildScalars() {
        Map<String, Function<NativeRoleHierarchyRecord, String>> m = new LinkedHashMap<>();
        m.put("source_role_id", r -> r.sourceRoleId);
        m.put("role_name", r -> r.sourceRoleName);
        m.put("related_role_source_id", r -> r.relatedRoleId);
        m.put("related_role_name", r -> r.relatedRoleName);
        m.put("relationship_type", r -> r.relationshipType);
        return m;
    }

    /** In-memory sink: collects only hierarchy edges; entitlement + sweeps are harmless no-ops (no DB). */
    private static final class CollectingSink implements NativeRoleRelationshipSink {
        final List<NativeRoleHierarchyRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeRoleEntitlementRepository.Outcome entitlement(NativeRoleRelationshipRecord r) {
            // role-entitlement edges are a different entity; the role-hierarchy endpoint ignores them.
            return NativeRoleEntitlementRepository.Outcome.INSERTED;
        }

        @Override
        public NativeRoleHierarchyRepository.Outcome hierarchy(NativeRoleHierarchyRecord r) {
            records.add(r);
            return NativeRoleHierarchyRepository.Outcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweepEntitlements(Collection<String> ids) {
            return new SoftDeleteSweeper.SweepResult("kf_role_entitlement", 0, 0, 0, true, "rest: no sweep");
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweepHierarchy(Collection<String> ids) {
            return new SoftDeleteSweeper.SweepResult("kf_role_hierarchy", 0, 0, 0, true, "rest: no sweep");
        }
    }
}
