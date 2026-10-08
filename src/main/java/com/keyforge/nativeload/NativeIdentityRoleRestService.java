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
 * KF Agent REST read service for native Identity-Role edges (one row per identity↔role relationship).
 * Same architecture as the other KF Agent REST services: it reuses the EXISTING native extraction path
 * end-to-end — the plugin REST page source ({@link NativeIdentityRoleClient}),
 * {@link NativeIdentityRoleParser}, and the {@link NativeIdentityRoleImportService} paging loop — and
 * persists nothing (a non-JDBC {@link CollectingSink} gathers the records in memory instead of writing
 * to PostgreSQL). No existing extraction or DB code is modified or deleted; the DB write is bypassed.
 *
 * <p><b>Field types per the actual native mapping / {@code kf_identity_role} schema</b> (verified):
 * {@code targets} is the only {@code jsonb} field (returned as JSON); {@code detection_assignment_ids}
 * is a {@code text} column (parser reads it with {@code text()}), returned as a string. The jsonb field
 * is not filterable; every scalar/text field is. The KeyForge {@code identityroleid} PK,
 * {@code record_hash}, {@code is_deleted}/{@code deleted_at} (not in the native record) and the lineage
 * envelope are excluded. This edge has no {@code source_hash}, and the native client has no server-side
 * {@code modifiedAfter} support (full-scan only).
 */
public final class NativeIdentityRoleRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The {@code targets} jsonb field is NOT filterable. */
    private static final Map<String, Function<NativeIdentityRoleRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeIdentityRolePageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeIdentityRoleRecord> all = collectAll(source);

        List<NativeIdentityRoleRecord> matched = new ArrayList<>();
        for (NativeIdentityRoleRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeIdentityRoleRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeIdentityRoleRecord r, Map<String, String> filters) {
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
    private List<NativeIdentityRoleRecord> collectAll(NativeIdentityRolePageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeIdentityRoleImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native identity-role extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Identity-Role fields, keyed by our DB column names (kf_identity_role order). */
    private Map<String, Object> toJson(NativeIdentityRoleRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("identity_id", r.identityId);
        m.put("identity_name", r.identityName);
        m.put("role_id", r.roleId);
        m.put("role_name", r.roleName);
        m.put("relationship_type", r.relationshipType);
        m.put("assignment_id", r.assignmentId);
        m.put("assigner", r.assigner);                               // Assignment.getAssigner()
        m.put("assigned_date", iso(r.assignedDate));                 // Assignment.getDate()
        m.put("start_date", iso(r.startDate));                       // Assignment.getStartDate()
        m.put("end_date", iso(r.endDate));                           // Assignment.getEndDate()
        m.put("source", r.source);                                   // Assignment.getSource()
        m.put("negative", r.negative);                               // Assignment.isNegative()
        m.put("manual", r.manual);                                   // Assignment.isManual()
        m.put("detection_assignment_ids", r.detectionAssignmentIds); // text
        m.put("comments", r.comments);
        m.put("future_assignment", r.futureAssignment);
        m.put("promoted_soft_permit", r.promotedSoftPermit);
        m.put("detection_date", iso(r.detectionDate));
        m.put("targets", node(r.targetsJson));                       // jsonb (now incl. displayName/elevatedAccess)
        return m;
    }

    private static Map<String, Function<NativeIdentityRoleRecord, String>> buildScalars() {
        Map<String, Function<NativeIdentityRoleRecord, String>> m = new LinkedHashMap<>();
        m.put("identity_id", r -> r.identityId);
        m.put("identity_name", r -> r.identityName);
        m.put("role_id", r -> r.roleId);
        m.put("role_name", r -> r.roleName);
        m.put("relationship_type", r -> r.relationshipType);
        m.put("assignment_id", r -> r.assignmentId);
        m.put("assigner", r -> r.assigner);
        m.put("assigned_date", r -> iso(r.assignedDate));
        m.put("start_date", r -> iso(r.startDate));
        m.put("end_date", r -> iso(r.endDate));
        m.put("source", r -> r.source);
        m.put("negative", r -> str(r.negative));
        m.put("manual", r -> str(r.manual));
        m.put("detection_assignment_ids", r -> r.detectionAssignmentIds);
        m.put("comments", r -> r.comments);
        m.put("future_assignment", r -> str(r.futureAssignment));
        m.put("promoted_soft_permit", r -> str(r.promotedSoftPermit));
        m.put("detection_date", r -> iso(r.detectionDate));
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
    private static final class CollectingSink implements NativeIdentityRoleSink {
        final List<NativeIdentityRoleRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeIdentityRoleRepository.UpsertOutcome upsert(NativeIdentityRoleRecord record) {
            records.add(record);
            return NativeIdentityRoleRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
