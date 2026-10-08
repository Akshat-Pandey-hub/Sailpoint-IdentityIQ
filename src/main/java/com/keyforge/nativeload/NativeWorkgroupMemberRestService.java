package com.keyforge.nativeload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KF Agent REST read service for native workgroup members (one row per workgroup↔member identity edge, from
 * {@code Identity.getWorkgroups()} → {@code kf_workgroup_member}). Unlike most entities, the native
 * {@link NativeWorkgroupMembershipImportService} is DB-coupled — its {@code run(Connection)} writes JDBC — so
 * the REST path cannot reuse the import service without touching PostgreSQL. It therefore reuses the DB-free
 * {@link NativeWorkgroupMembershipClient} + {@link NativeWorkgroupMembershipParser} and replays the SAME
 * cursor loop (page → parse → collect, advancing on {@code nextStart} until {@code done}) into an in-memory
 * list, with no repository, no connection and no upsert/sweep. No existing extraction or DB code is modified.
 *
 * <p><b>REST contract</b> (verified field-by-field against {@link NativeWorkgroupMembershipParser} +
 * {@link NativeWorkgroupMembershipRepository}'s upsert bindings): 7 SailPoint-facing fields, all scalar (no
 * structured/jsonb, no timestamps). {@code member_is_workgroup} is a boolean; every other field is text.
 * {@code workgroup_id}/{@code identity_id} are the <b>native SailPoint ids</b> carried by the record
 * ({@code r.workgroupId}/{@code r.identityId}) — NOT the KeyForge canonical UUIDs that the DB columns of the
 * same name hold ({@code ParquetIds.canonicalUuid(...)}), which are excluded. {@code workgroup_name} is a
 * genuine native field on the record (populated by the parser, used in the record hash) that the DB table
 * does not persist as its own column — exposed here because REST reuses the native record. All 7 are
 * filterable. Excluded as technical: the KeyForge {@code id} PK, the canonical-UUID {@code workgroup_id}/
 * {@code identity_id} DB columns, {@code record_hash}, the lineage envelope ({@code src_system}/
 * {@code src_interface}/{@code src_object_type}/{@code src_object_id}/{@code src_natural_key}/
 * {@code extraction_run_id}/{@code extracted_at}) and the soft-delete columns ({@code is_deleted}/
 * {@code deleted_at}). No native {@code modifiedAfter} (full-scan only).
 */
public final class NativeWorkgroupMemberRestService {

    /** Transport seam: {@code NativeWorkgroupMembershipClient::fetch} in production, a fake in tests. */
    @FunctionalInterface
    public interface PageSource {
        String fetchPage(int start, int limit);
    }

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final NativeWorkgroupMembershipParser parser = new NativeWorkgroupMembershipParser();

    /** Scalar response field name -> value extractor (all 7 scalar, all filterable). */
    private static final Map<String, Function<NativeWorkgroupMembershipRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(PageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeWorkgroupMembershipRecord> all = collectAll(source);

        List<NativeWorkgroupMembershipRecord> matched = new ArrayList<>();
        for (NativeWorkgroupMembershipRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeWorkgroupMembershipRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeWorkgroupMembershipRecord r, Map<String, String> filters) {
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

    /**
     * Walks the native source with the SAME cursor loop the import service uses, minus the JDBC upsert (DB
     * bypass): page → {@link NativeWorkgroupMembershipParser#parse} → collect, advancing on {@code nextStart}
     * until {@code done} (with a non-advancing guard).
     */
    private List<NativeWorkgroupMembershipRecord> collectAll(PageSource source) {
        List<NativeWorkgroupMembershipRecord> all = new ArrayList<>();
        int start = 0;
        while (true) {
            NativeWorkgroupMembershipPage page = parser.parse(source.fetchPage(start, INTERNAL_PAGE_SIZE));
            all.addAll(page.rows);
            if (page.done) {
                break;
            }
            if (page.nextStart <= start) {
                throw new NativeImportException("Membership pagination did not advance (start=" + start + ")");
            }
            start = page.nextStart;
        }
        return all;
    }

    /** The SailPoint-facing fields, keyed by our business-field names. */
    private Map<String, Object> toJson(NativeWorkgroupMembershipRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("workgroup_id", r.workgroupId);             // native SailPoint workgroup id
        m.put("workgroup_name", r.workgroupName);         // native workgroup name (record-only, no DB column)
        m.put("identity_id", r.identityId);               // native SailPoint member identity id
        m.put("member_name", r.identityName);             // member's name (DB column member_name)
        m.put("first_name", r.firstName);
        m.put("last_name", r.lastName);
        m.put("member_is_workgroup", r.memberIsWorkgroup); // boolean
        return m;
    }

    private static Map<String, Function<NativeWorkgroupMembershipRecord, String>> buildScalars() {
        Map<String, Function<NativeWorkgroupMembershipRecord, String>> m = new LinkedHashMap<>();
        m.put("workgroup_id", r -> r.workgroupId);
        m.put("workgroup_name", r -> r.workgroupName);
        m.put("identity_id", r -> r.identityId);
        m.put("member_name", r -> r.identityName);
        m.put("first_name", r -> r.firstName);
        m.put("last_name", r -> r.lastName);
        m.put("member_is_workgroup", r -> r.memberIsWorkgroup == null ? null : r.memberIsWorkgroup.toString());
        return m;
    }
}
