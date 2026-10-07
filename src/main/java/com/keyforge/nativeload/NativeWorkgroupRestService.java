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
 * KF Agent REST read service for native Workgroups ({@code sailpoint.object.Identity} with
 * {@code workgroup=true}). Same architecture as the Entitlement/Identity/Application/Account REST
 * services: it reuses the EXISTING native extraction path end-to-end — the plugin REST page source
 * ({@link NativeWorkgroupClient}), {@link NativeWorkgroupParser}, and the
 * {@link NativeWorkgroupImportService} paging loop — and persists nothing (a non-JDBC
 * {@link CollectingSink} gathers the records in memory instead of writing to PostgreSQL). No existing
 * extraction or DB code is modified or deleted; the DB write is bypassed here by construction.
 *
 * <p><b>Generic filtering</b> on any scalar response field by its response/mapped name (exact,
 * AND-combined, over the full population before {@code start}/{@code limit}). The 2 jsonb fields
 * ({@code capabilities}, {@code attributes}) are not filterable. The KeyForge {@code workgroupid} PK,
 * {@code record_hash}, {@code is_deleted}/{@code deleted_at} (not in the native record) and the lineage
 * envelope are excluded. Workgroup has no {@code source_hash}, and the native Workgroup client has no
 * server-side {@code modifiedAfter} support (full-scan only).
 */
public final class NativeWorkgroupRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The 2 jsonb fields are intentionally NOT filterable. */
    private static final Map<String, Function<NativeWorkgroupRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeWorkgroupPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeWorkgroupRecord> all = collectAll(source);

        List<NativeWorkgroupRecord> matched = new ArrayList<>();
        for (NativeWorkgroupRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeWorkgroupRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeWorkgroupRecord r, Map<String, String> filters) {
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
    private List<NativeWorkgroupRecord> collectAll(NativeWorkgroupPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeWorkgroupImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native workgroup extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Workgroup fields, keyed by our DB column names (kf_workgroup order). */
    private Map<String, Object> toJson(NativeWorkgroupRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("display_name", r.displayName);
        m.put("displayable_name", r.displayableName);
        m.put("email", r.email);
        m.put("type", r.type);
        m.put("description", r.description);
        m.put("notification_option", r.notificationOption);
        m.put("inactive", r.inactive);
        m.put("is_workgroup", r.workgroup);
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("capabilities", node(r.capabilitiesJson));
        m.put("attributes", node(r.attributesJson));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeWorkgroupRecord, String>> buildScalars() {
        Map<String, Function<NativeWorkgroupRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("display_name", r -> r.displayName);
        m.put("displayable_name", r -> r.displayableName);
        m.put("email", r -> r.email);
        m.put("type", r -> r.type);
        m.put("description", r -> r.description);
        m.put("notification_option", r -> r.notificationOption);
        m.put("inactive", r -> str(r.inactive));
        m.put("is_workgroup", r -> str(r.workgroup));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
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
    private static final class CollectingSink implements NativeWorkgroupSink {
        final List<NativeWorkgroupRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeWorkgroupRepository.UpsertOutcome upsert(NativeWorkgroupRecord record) {
            records.add(record);
            return NativeWorkgroupRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepWorkgroupIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
