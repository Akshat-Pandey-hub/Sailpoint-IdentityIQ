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
 * KF Agent REST read service for native Roles ({@code sailpoint.object.Bundle}). Same architecture as the
 * other KF Agent REST services: it reuses the EXISTING native extraction path end-to-end — the plugin
 * REST page source ({@link NativeRoleClient}), {@link NativeRoleParser}, and the
 * {@link NativeRoleImportService} paging loop — and persists nothing (a non-JDBC {@link CollectingSink}
 * gathers the records in memory instead of writing to PostgreSQL). No existing extraction or DB code is
 * modified or deleted; the DB write is bypassed here by construction.
 *
 * <p><b>Field types per the actual native mapping / {@code kf_role} schema</b> (verified, not assumed):
 * only {@code descriptions}, {@code attributes} and {@code selector} are real {@code jsonb} (returned as
 * JSON objects/arrays). {@code role_type_definition}, {@code applications}, {@code monitored_applications},
 * {@code scorecard} and {@code selector_summary} are {@code text} columns (the parser reads them with
 * {@code text()}), so they are returned as strings — exactly as stored. The 3 jsonb fields are not
 * filterable; every scalar/text field is. The KeyForge {@code roleid} PK, {@code record_hash},
 * {@code is_deleted}/{@code deleted_at} (not in the native record) and the lineage envelope are excluded.
 * Role has no {@code source_hash}.
 */
public final class NativeRoleRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The 3 jsonb fields are intentionally NOT filterable. */
    private static final Map<String, Function<NativeRoleRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize. {@code source} is injected so the caller decides the
     * server-side {@code modifiedAfter} bound (and so this is unit-testable with a canned page source).
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeRolePageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeRoleRecord> all = collectAll(source);

        List<NativeRoleRecord> matched = new ArrayList<>();
        for (NativeRoleRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeRoleRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeRoleRecord r, Map<String, String> filters) {
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
    private List<NativeRoleRecord> collectAll(NativeRolePageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeRoleImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native role extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Role fields, keyed by our DB column names (kf_role order). */
    private Map<String, Object> toJson(NativeRoleRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("display_name", r.displayName);
        m.put("displayable_name", r.displayableName);
        m.put("full_name", r.fullName);
        m.put("description", r.description);
        m.put("type", r.type);
        m.put("assignment_id", r.assignmentId);
        m.put("activity_enabled", r.activityEnabled);
        m.put("allow_duplicate_accounts", r.allowDuplicateAccounts);
        m.put("allow_multiple_assignments", r.allowMultipleAssignments);
        m.put("auto_promotion", r.autoPromotion);
        m.put("differencable", r.differencable);
        m.put("iiq_elevated_access", r.iiqElevatedAccess);
        m.put("merge_templates", r.mergeTemplates);
        m.put("or_profiles", r.orProfiles);
        m.put("pending_delete", r.pendingDelete);
        m.put("has_selector", r.hasSelector);
        m.put("risk_score_weight", r.riskScoreWeight);
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("activation_date", iso(r.activationDate));
        m.put("deactivation_date", iso(r.deactivationDate));
        m.put("descriptions", node(r.descriptionsJson));   // jsonb
        m.put("attributes", node(r.attributesJson));       // jsonb
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("role_type_definition", r.roleTypeDefinition);          // text
        m.put("applications", r.applicationsJson);                    // text
        m.put("monitored_applications", r.monitoredApplicationsJson); // text
        m.put("scorecard", r.scorecard);                              // text
        m.put("selector_summary", r.selectorSummary);                 // text
        m.put("selector", node(r.selectorJson));           // jsonb
        return m;
    }

    private static Map<String, Function<NativeRoleRecord, String>> buildScalars() {
        Map<String, Function<NativeRoleRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("display_name", r -> r.displayName);
        m.put("displayable_name", r -> r.displayableName);
        m.put("full_name", r -> r.fullName);
        m.put("description", r -> r.description);
        m.put("type", r -> r.type);
        m.put("assignment_id", r -> r.assignmentId);
        m.put("activity_enabled", r -> str(r.activityEnabled));
        m.put("allow_duplicate_accounts", r -> str(r.allowDuplicateAccounts));
        m.put("allow_multiple_assignments", r -> str(r.allowMultipleAssignments));
        m.put("auto_promotion", r -> str(r.autoPromotion));
        m.put("differencable", r -> str(r.differencable));
        m.put("iiq_elevated_access", r -> str(r.iiqElevatedAccess));
        m.put("merge_templates", r -> str(r.mergeTemplates));
        m.put("or_profiles", r -> str(r.orProfiles));
        m.put("pending_delete", r -> str(r.pendingDelete));
        m.put("has_selector", r -> str(r.hasSelector));
        m.put("risk_score_weight", r -> r.riskScoreWeight == null ? null : String.valueOf(r.riskScoreWeight));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("activation_date", r -> iso(r.activationDate));
        m.put("deactivation_date", r -> iso(r.deactivationDate));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("role_type_definition", r -> r.roleTypeDefinition);
        m.put("applications", r -> r.applicationsJson);
        m.put("monitored_applications", r -> r.monitoredApplicationsJson);
        m.put("scorecard", r -> r.scorecard);
        m.put("selector_summary", r -> r.selectorSummary);
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
    private static final class CollectingSink implements NativeRoleSink {
        final List<NativeRoleRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeRoleRepository.UpsertOutcome upsert(NativeRoleRecord record) {
            records.add(record);
            return NativeRoleRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepRoleIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
