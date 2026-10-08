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
 * KF Agent REST read service for native Role-Entitlement edges (Bundle profile constraints/permissions →
 * {@code kf_role_entitlement}). The native source is the Bundle "relationship" extraction, which produces
 * BOTH role-entitlement and role-hierarchy edges through one import; this service collects only the
 * entitlement edges. Same architecture as the other KF Agent REST services: it reuses the EXISTING native
 * extraction path end-to-end — the plugin REST page source ({@link NativeRoleRelationshipClient}),
 * {@link NativeRoleRelationshipParser}, and the {@link NativeRoleRelationshipImportService} paging loop —
 * and persists nothing (a non-JDBC {@link CollectingSink} gathers the records in memory; its two sweep
 * methods return a skipped result so the import's end-of-scan sweep is a harmless no-op). No existing
 * extraction or DB code is modified or deleted; the DB write is bypassed by construction.
 *
 * <p><b>REST contract</b> (verified from {@link NativeRoleRelationshipRecord} + {@code kf_role_entitlement}):
 * 16 SailPoint-facing fields. {@code filter_value} and {@code permission_rights_list} are structured
 * (returned as JSON, not strings) and are NOT filterable; the other 14 are scalar and filterable.
 * Excluded as KeyForge-only/technical: the {@code roleentitlementid} PK and the KeyForge canonical-UUID
 * join keys {@code role_id}/{@code entitlement_id}, plus {@code src_object_id}, {@code src_natural_key},
 * {@code record_hash}, {@code source_system}/{@code source_interface}/{@code source_object_type},
 * {@code extraction_run_id}, {@code extracted_at}, {@code is_deleted}, {@code deleted_at}. The SailPoint
 * role reference is kept as {@code source_role_id}. If IIQ has no role-entitlement edges, the endpoint
 * returns an empty array.
 */
public final class NativeRoleEntitlementRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    /** Scalar response field name -> value extractor. The 2 structured fields are NOT filterable. */
    private static final Map<String, Function<NativeRoleRelationshipRecord, String>> SCALARS = buildScalars();

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

        List<NativeRoleRelationshipRecord> all = collectAll(source);

        List<NativeRoleRelationshipRecord> matched = new ArrayList<>();
        for (NativeRoleRelationshipRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeRoleRelationshipRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeRoleRelationshipRecord r, Map<String, String> filters) {
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
    private List<NativeRoleRelationshipRecord> collectAll(NativeRoleRelationshipPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // run() owns its own runId + complete-scan validation; the collecting sink persists nothing
            // and its sweeps are skipped no-ops, so no PostgreSQL is touched.
            new NativeRoleRelationshipImportService(source, sink, INTERNAL_PAGE_SIZE).run();
        } catch (SQLException e) {
            throw new NativeImportException("native role-entitlement extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Role-Entitlement fields, keyed by our DB column names (kf_role_entitlement order). */
    private Map<String, Object> toJson(NativeRoleRelationshipRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_role_id", r.sourceBundleId);
        m.put("role_name", r.roleName);
        m.put("entitlement_type", r.entitlementType);
        m.put("application_id", r.applicationId);
        m.put("application_name", r.application);
        m.put("profile_ordinal", r.profileOrdinal);
        m.put("constraint_path", r.constraintPath);
        m.put("filter_expression", r.filterExpression);
        m.put("filter_value", r.filterValue);                 // structured (native Object) -> JSON
        m.put("filter_operation", r.filterOperation);
        m.put("attribute_name", r.attributeName);
        m.put("attribute_value", r.attributeValue);
        m.put("permission_target", r.permissionTarget);
        m.put("permission_rights", r.permissionRights);
        m.put("permission_rights_list", r.permissionRightsList); // structured (List) -> JSON array
        m.put("permission_annotation", r.permissionAnnotation);
        return m;
    }

    private static Map<String, Function<NativeRoleRelationshipRecord, String>> buildScalars() {
        Map<String, Function<NativeRoleRelationshipRecord, String>> m = new LinkedHashMap<>();
        m.put("source_role_id", r -> r.sourceBundleId);
        m.put("role_name", r -> r.roleName);
        m.put("entitlement_type", r -> r.entitlementType);
        m.put("application_id", r -> r.applicationId);
        m.put("application_name", r -> r.application);
        m.put("profile_ordinal", r -> String.valueOf(r.profileOrdinal));
        m.put("constraint_path", r -> r.constraintPath);
        m.put("filter_expression", r -> r.filterExpression);
        m.put("filter_operation", r -> r.filterOperation);
        m.put("attribute_name", r -> r.attributeName);
        m.put("attribute_value", r -> r.attributeValue);
        m.put("permission_target", r -> r.permissionTarget);
        m.put("permission_rights", r -> r.permissionRights);
        m.put("permission_annotation", r -> r.permissionAnnotation);
        return m;
    }

    /** In-memory sink: collects only entitlement edges; hierarchy + sweeps are harmless no-ops (no DB). */
    private static final class CollectingSink implements NativeRoleRelationshipSink {
        final List<NativeRoleRelationshipRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeRoleEntitlementRepository.Outcome entitlement(NativeRoleRelationshipRecord r) {
            records.add(r);
            return NativeRoleEntitlementRepository.Outcome.INSERTED;
        }

        @Override
        public NativeRoleHierarchyRepository.Outcome hierarchy(NativeRoleHierarchyRecord r) {
            // role-hierarchy edges are a different entity; the role-entitlements endpoint ignores them.
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
