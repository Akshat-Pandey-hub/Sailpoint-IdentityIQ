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
 * KF Agent REST read service for native Applications ({@code sailpoint.object.Application}). Same
 * architecture as {@link NativeEntitlementRestService} / {@link NativeIdentityRestService}: it reuses the
 * EXISTING native extraction path end-to-end — the plugin REST page source, {@link NativeApplicationParser},
 * and the {@link NativeApplicationImportService} paging loop — and persists nothing (a non-JDBC
 * {@link CollectingSink} gathers the records in memory instead of writing to PostgreSQL). No existing
 * extraction or DB code is modified or deleted; the DB write is bypassed here by construction.
 *
 * <p><b>Generic filtering</b> on any scalar response field by its response/mapped name (exact, AND-combined,
 * over the full population before {@code start}/{@code limit}). The 10 jsonb fields are not filterable.
 * The KeyForge {@code applicationid} PK, {@code record_hash}, {@code is_deleted}/{@code deleted_at} (not in
 * the native record) and the lineage envelope are excluded from the response.
 */
public final class NativeApplicationRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The 10 jsonb fields are intentionally NOT filterable. */
    private static final Map<String, Function<NativeApplicationRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize. {@code source} is injected so the caller decides the
     * server-side {@code modifiedAfter} bound (and so this is unit-testable with a canned page source).
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeApplicationPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeApplicationRecord> all = collectAll(source);

        List<NativeApplicationRecord> matched = new ArrayList<>();
        for (NativeApplicationRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeApplicationRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeApplicationRecord r, Map<String, String> filters) {
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
    private List<NativeApplicationRecord> collectAll(NativeApplicationPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep() is never invoked; no PostgreSQL is touched.
            new NativeApplicationImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native application extraction failed: " + e.getMessage(), e);
        }
        return sink.records;
    }

    /** The SailPoint-facing Application fields, keyed by our DB column names (kf_application order). */
    private Map<String, Object> toJson(NativeApplicationRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("description", r.description);
        m.put("type", r.type);
        m.put("connector", r.connector);
        m.put("features_string", r.featuresString);
        m.put("profile_class", r.profileClass);
        m.put("proxied_name", r.proxiedName);
        m.put("cluster", r.cluster);
        m.put("icon", r.icon);
        m.put("aggregation_types", r.aggregationTypes);
        m.put("before_provisioning_rule", r.beforeProvisioningRule);
        m.put("after_provisioning_rule", r.afterProvisioningRule);
        m.put("account_schema_correlation_rule", r.accountSchemaCorrelationRule);
        m.put("account_schema_customization_rule", r.accountSchemaCustomizationRule);
        m.put("account_schema_creation_rule", r.accountSchemaCreationRule);
        m.put("account_schema_refresh_rule", r.accountSchemaRefreshRule);
        m.put("application_creation_rule", r.applicationCreationRule);
        m.put("account_schema_correlation_rule_id", r.accountSchemaCorrelationRuleId);
        m.put("account_schema_customization_rule_id", r.accountSchemaCustomizationRuleId);
        m.put("account_schema_creation_rule_id", r.accountSchemaCreationRuleId);
        m.put("account_schema_refresh_rule_id", r.accountSchemaRefreshRuleId);
        m.put("application_creation_rule_id", r.applicationCreationRuleId);
        m.put("score", r.score);
        m.put("authoritative", r.authoritative);
        m.put("case_insensitive", r.caseInsensitive);
        m.put("logical", r.logical);
        m.put("composite", r.composite);
        m.put("authentication_resource", r.authenticationResource);
        m.put("activity_enabled", r.activityEnabled);
        m.put("in_maintenance", r.inMaintenance);
        m.put("manages_other_apps", r.managesOtherApps);
        m.put("native_change_detection_enabled", r.nativeChangeDetectionEnabled);
        m.put("supports_provisioning", r.supportsProvisioning);
        m.put("supports_account_only", r.supportsAccountOnly);
        m.put("supports_additional_accounts", r.supportsAdditionalAccounts);
        m.put("supports_authenticate", r.supportsAuthenticate);
        m.put("supports_group_provisioning", r.supportsGroupProvisioning);
        m.put("supports_direct_permissions", r.supportsDirectPermissions);
        m.put("sync_provisioning", r.syncProvisioning);
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("secondary_owners", node(r.secondaryOwnersJson));
        m.put("remediators", node(r.remediatorsJson));
        m.put("dependencies", node(r.dependenciesJson));
        m.put("schemas", node(r.schemasJson));
        m.put("descriptions", node(r.descriptionsJson));
        m.put("attributes", node(r.attributesJson));
        m.put("provisioning_config", r.provisioningConfig);
        m.put("account_correlation_config", r.accountCorrelationConfig);
        m.put("manager_correlation_rule", r.managerCorrelationRule);
        m.put("manager_correlation_filter", r.managerCorrelationFilter);
        m.put("service_account_filter", node(r.serviceAccountFilterJson));
        m.put("rpa_account_filter", node(r.rpaAccountFilterJson));
        m.put("disable_account_filter", node(r.disableAccountFilterJson));
        m.put("lock_account_filter", node(r.lockAccountFilterJson));
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeApplicationRecord, String>> buildScalars() {
        Map<String, Function<NativeApplicationRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("description", r -> r.description);
        m.put("type", r -> r.type);
        m.put("connector", r -> r.connector);
        m.put("features_string", r -> r.featuresString);
        m.put("profile_class", r -> r.profileClass);
        m.put("proxied_name", r -> r.proxiedName);
        m.put("cluster", r -> r.cluster);
        m.put("icon", r -> r.icon);
        m.put("aggregation_types", r -> r.aggregationTypes);
        m.put("before_provisioning_rule", r -> r.beforeProvisioningRule);
        m.put("after_provisioning_rule", r -> r.afterProvisioningRule);
        m.put("account_schema_correlation_rule", r -> r.accountSchemaCorrelationRule);
        m.put("account_schema_customization_rule", r -> r.accountSchemaCustomizationRule);
        m.put("account_schema_creation_rule", r -> r.accountSchemaCreationRule);
        m.put("account_schema_refresh_rule", r -> r.accountSchemaRefreshRule);
        m.put("application_creation_rule", r -> r.applicationCreationRule);
        m.put("account_schema_correlation_rule_id", r -> r.accountSchemaCorrelationRuleId);
        m.put("account_schema_customization_rule_id", r -> r.accountSchemaCustomizationRuleId);
        m.put("account_schema_creation_rule_id", r -> r.accountSchemaCreationRuleId);
        m.put("account_schema_refresh_rule_id", r -> r.accountSchemaRefreshRuleId);
        m.put("application_creation_rule_id", r -> r.applicationCreationRuleId);
        m.put("score", r -> r.score == null ? null : String.valueOf(r.score));
        m.put("authoritative", r -> str(r.authoritative));
        m.put("case_insensitive", r -> str(r.caseInsensitive));
        m.put("logical", r -> str(r.logical));
        m.put("composite", r -> str(r.composite));
        m.put("authentication_resource", r -> str(r.authenticationResource));
        m.put("activity_enabled", r -> str(r.activityEnabled));
        m.put("in_maintenance", r -> str(r.inMaintenance));
        m.put("manages_other_apps", r -> str(r.managesOtherApps));
        m.put("native_change_detection_enabled", r -> str(r.nativeChangeDetectionEnabled));
        m.put("supports_provisioning", r -> str(r.supportsProvisioning));
        m.put("supports_account_only", r -> str(r.supportsAccountOnly));
        m.put("supports_additional_accounts", r -> str(r.supportsAdditionalAccounts));
        m.put("supports_authenticate", r -> str(r.supportsAuthenticate));
        m.put("supports_group_provisioning", r -> str(r.supportsGroupProvisioning));
        m.put("supports_direct_permissions", r -> str(r.supportsDirectPermissions));
        m.put("sync_provisioning", r -> str(r.syncProvisioning));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("provisioning_config", r -> r.provisioningConfig);
        m.put("account_correlation_config", r -> r.accountCorrelationConfig);
        m.put("manager_correlation_rule", r -> r.managerCorrelationRule);
        m.put("manager_correlation_filter", r -> r.managerCorrelationFilter);
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
    private static final class CollectingSink implements NativeApplicationSink {
        final List<NativeApplicationRecord> records = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeApplicationRepository.UpsertOutcome upsert(NativeApplicationRecord record) {
            records.add(record);
            return NativeApplicationRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweep(Collection<String> keepApplicationIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
