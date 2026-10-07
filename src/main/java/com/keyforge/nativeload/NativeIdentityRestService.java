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
 * KF Agent REST read service for native Identities ({@code sailpoint.object.Identity}). Same architecture
 * as {@link NativeEntitlementRestService}: it reuses the EXISTING native extraction path end-to-end — the
 * plugin REST page source, {@link NativeIdentityParser}, and the {@link NativeIdentityImportService}
 * paging loop — and persists nothing (a non-JDBC {@link CollectingSink} gathers the records in memory
 * instead of writing to PostgreSQL). No existing extraction or DB code is modified or deleted; the DB
 * write is bypassed here by construction.
 *
 * <p><b>Generic filtering.</b> Any scalar response field can be used as a query filter under its own
 * response/mapped name (e.g. {@code source_id}, {@code name}, {@code email}, {@code type},
 * {@code inactive}, {@code correlated}, {@code is_workgroup}). Multiple filters AND together, each an
 * exact match. Filtering runs over the COMPLETE population (never a single native page), then the
 * {@code start}/{@code limit} output window is applied, then rows are serialized to the SailPoint-facing
 * fields (the KeyForge {@code userid} PK and lineage/ingestion metadata are excluded — not SailPoint data).
 */
public final class NativeIdentityRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. Nested jsonb fields are intentionally NOT filterable. */
    private static final Map<String, Function<NativeIdentityRecord, String>> SCALARS = buildScalars();

    /** The field names a caller may filter on (the scalar subset of the Identity response fields). */
    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize. {@code source} is injected so the caller decides the
     * server-side {@code modifiedAfter} bound (and so this is unit-testable with a canned page source).
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeIdentityPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeIdentityRecord> all = collectAll(source);

        List<NativeIdentityRecord> matched = new ArrayList<>();
        for (NativeIdentityRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeIdentityRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeIdentityRecord r, Map<String, String> filters) {
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
    private List<NativeIdentityRecord> collectAll(NativeIdentityPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeIdentityImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native identity extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Identity fields, keyed by our DB column names. Excludes userid PK + lineage. */
    private Map<String, Object> toJson(NativeIdentityRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("display_name", r.displayName);
        m.put("displayable_name", r.displayableName);
        m.put("first_name", r.firstName);
        m.put("last_name", r.lastName);
        m.put("email", r.email);
        m.put("inactive", r.inactive);
        m.put("type", r.type);
        m.put("correlated", r.correlated);
        m.put("manager_status", r.managerStatus);
        m.put("manager_id", r.managerId);
        m.put("manager_name", r.managerName);
        m.put("administrator_id", r.administratorId);
        m.put("administrator_name", r.administratorName);
        m.put("accounts", node(r.accountsJson));
        m.put("assigned_roles", node(r.assignedRolesJson));
        m.put("detected_roles", node(r.detectedRolesJson));
        m.put("role_assignments", node(r.roleAssignmentsJson));
        m.put("role_detections", node(r.roleDetectionsJson));
        m.put("role_requests", node(r.roleRequestsJson));
        m.put("mitigation_expirations", node(r.mitigationExpirationsJson));
        m.put("capabilities", node(r.capabilitiesJson));
        m.put("controlled_scopes", node(r.controlledScopesJson));
        m.put("attributes", node(r.attributesJson));
        m.put("score", r.score);
        m.put("full_name", r.fullName);
        m.put("is_protected", r.protectedFlag);
        m.put("is_needs_refresh", r.needsRefresh);
        m.put("is_correlated_overridden", r.correlatedOverridden);
        m.put("is_workgroup", r.workgroup);
        m.put("auth_application", r.authApplication);
        m.put("auth_account", r.authAccount);
        m.put("pending_refresh_workflow", r.pendingRefreshWorkflow);
        m.put("password_expiration", iso(r.passwordExpiration));
        m.put("auth_lock_start", iso(r.authLockStart));
        m.put("use_by", iso(r.useBy));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        m.put("last_refresh", iso(r.lastRefresh));
        m.put("last_login", iso(r.lastLogin));
        return m;
    }

    private static Map<String, Function<NativeIdentityRecord, String>> buildScalars() {
        Map<String, Function<NativeIdentityRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("display_name", r -> r.displayName);
        m.put("displayable_name", r -> r.displayableName);
        m.put("first_name", r -> r.firstName);
        m.put("last_name", r -> r.lastName);
        m.put("email", r -> r.email);
        m.put("inactive", r -> str(r.inactive));
        m.put("type", r -> r.type);
        m.put("correlated", r -> str(r.correlated));
        m.put("manager_status", r -> str(r.managerStatus));
        m.put("manager_id", r -> r.managerId);
        m.put("manager_name", r -> r.managerName);
        m.put("administrator_id", r -> r.administratorId);
        m.put("administrator_name", r -> r.administratorName);
        m.put("score", r -> r.score);
        m.put("full_name", r -> r.fullName);
        m.put("is_protected", r -> str(r.protectedFlag));
        m.put("is_needs_refresh", r -> str(r.needsRefresh));
        m.put("is_correlated_overridden", r -> str(r.correlatedOverridden));
        m.put("is_workgroup", r -> str(r.workgroup));
        m.put("auth_application", r -> r.authApplication);
        m.put("auth_account", r -> r.authAccount);
        m.put("pending_refresh_workflow", r -> r.pendingRefreshWorkflow);
        m.put("password_expiration", r -> iso(r.passwordExpiration));
        m.put("auth_lock_start", r -> iso(r.authLockStart));
        m.put("use_by", r -> iso(r.useBy));
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
        m.put("last_refresh", r -> iso(r.lastRefresh));
        m.put("last_login", r -> iso(r.lastLogin));
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
    private static final class CollectingSink implements NativeIdentitySink {
        final List<NativeIdentityRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeIdentityRepository.UpsertOutcome upsert(NativeIdentityRecord record) {
            records.add(record);
            return NativeIdentityRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepUserids) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
