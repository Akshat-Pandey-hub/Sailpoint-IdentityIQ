package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * KF Agent REST read service for native provisioning items (AttributeRequest/PermissionRequest derived
 * from {@code sailpoint.object.ProvisioningPlan} → {@code kf_provisioning_item}). The items are DERIVED as
 * part of the SHARED ProvisioningTransaction native import (each transaction carries derived items); there
 * is no separate item client/import. This service therefore reuses {@link NativeProvisioningTxnClient} +
 * {@link NativeProvisioningTxnImportService} and a non-JDBC {@link CollectingSink} that collects only the
 * ITEM rows (no-ops transaction upserts). No existing extraction or DB code is modified or deleted; the DB
 * write is bypassed by construction.
 *
 * <p><b>REST contract</b> (verified from {@link NativeProvisioningItemRecord} + {@code kf_provisioning_item}):
 * 17 SailPoint-facing fields. {@code value_json} is the only {@code jsonb} field (returned as JSON);
 * {@code value}, {@code permission_target}, {@code permission_rights} are {@code text} (strings),
 * {@code assignment} is a boolean, {@code item_index} is an integer. The jsonb field is not filterable;
 * the other 16 are scalar and filterable. The KeyForge {@code provisioningitemid} PK, {@code record_hash},
 * {@code src_object_id}/{@code src_natural_key}, {@code is_deleted}/{@code deleted_at} (not in the native
 * record) and the lineage envelope are excluded. No {@code source_hash}; no native {@code modifiedAfter}
 * (full-scan only).
 */
public final class NativeProvisioningItemRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The {@code value_json} jsonb field is NOT filterable. */
    private static final Map<String, Function<NativeProvisioningItemRecord, String>> SCALARS = buildScalars();

    public static final Set<String> FILTERABLE_FIELDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(SCALARS.keySet()));

    /**
     * Collect → filter → window → serialize.
     *
     * @throws IllegalArgumentException if a filter names a field that is not filterable
     */
    public List<Map<String, Object>> fetch(NativeProvisioningTxnPageSource source,
                                           Map<String, String> filters, Integer start, Integer limit) {
        if (filters != null) {
            for (String key : filters.keySet()) {
                if (!SCALARS.containsKey(key)) {
                    throw new IllegalArgumentException("unknown filter field '" + key
                            + "'; filterable fields are: " + FILTERABLE_FIELDS);
                }
            }
        }

        List<NativeProvisioningItemRecord> all = collectAll(source);

        List<NativeProvisioningItemRecord> matched = new ArrayList<>();
        for (NativeProvisioningItemRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeProvisioningItemRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeProvisioningItemRecord r, Map<String, String> filters) {
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

    /** Walks the native source to completion via the EXISTING ProvisioningTransaction import; collecting sink = no DB. */
    private List<NativeProvisioningItemRecord> collectAll(NativeProvisioningTxnPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep* methods are never invoked; no PostgreSQL is touched.
            new NativeProvisioningTxnImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native provisioning-item extraction failed: " + e.getMessage(), e);
        }
        return sink.items;
    }

    /** The SailPoint-facing item fields, keyed by our DB column names (kf_provisioning_item order). */
    private Map<String, Object> toJson(NativeProvisioningItemRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txn_source_id", r.txnSourceId);
        m.put("identity_name", r.identityName);
        m.put("item_type", r.itemType);
        m.put("operation", r.operation);
        m.put("application_name", r.applicationName);
        m.put("native_identity", r.nativeIdentity);
        m.put("instance", r.instance);
        m.put("account_operation", r.accountOperation);
        m.put("name", r.name);
        m.put("value", r.value);
        m.put("value_json", node(r.valueJson));   // jsonb
        m.put("assignment_id", r.assignmentId);
        m.put("assignment", r.assignment);
        m.put("permission_target", r.permissionTarget);
        m.put("permission_rights", r.permissionRights);
        m.put("request_id", r.requestId);
        m.put("item_index", r.itemIndex);
        return m;
    }

    private static Map<String, Function<NativeProvisioningItemRecord, String>> buildScalars() {
        Map<String, Function<NativeProvisioningItemRecord, String>> m = new LinkedHashMap<>();
        m.put("txn_source_id", r -> r.txnSourceId);
        m.put("identity_name", r -> r.identityName);
        m.put("item_type", r -> r.itemType);
        m.put("operation", r -> r.operation);
        m.put("application_name", r -> r.applicationName);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("instance", r -> r.instance);
        m.put("account_operation", r -> r.accountOperation);
        m.put("name", r -> r.name);
        m.put("value", r -> r.value);
        m.put("assignment_id", r -> r.assignmentId);
        m.put("assignment", r -> r.assignment == null ? null : r.assignment.toString());
        m.put("permission_target", r -> r.permissionTarget);
        m.put("permission_rights", r -> r.permissionRights);
        m.put("request_id", r -> r.requestId);
        m.put("item_index", r -> r.itemIndex == null ? null : String.valueOf(r.itemIndex));
        return m;
    }

    /** Re-hydrate a pre-serialized jsonb string into a JSON value/object/array; null stays null. */
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

    /** In-memory sink: collects only item rows; transaction upserts + sweeps are no-ops (no DB). */
    private static final class CollectingSink implements NativeProvisioningTxnSink {
        final List<NativeProvisioningItemRecord> items = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeProvisioningTxnRepository.UpsertOutcome upsertTxn(NativeProvisioningTxnRecord record) {
            // the transaction itself is a separate entity; the items endpoint ignores it.
            return NativeProvisioningTxnRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public NativeProvisioningItemRepository.UpsertOutcome upsertItem(NativeProvisioningItemRecord record) {
            items.add(record);
            return NativeProvisioningItemRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweepTxns(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }

        @Override
        public SoftDeleteSweeper.SweepResult sweepItems(Collection<String> keepIds) {
            throw new UnsupportedOperationException("REST path never sweeps (sweepDeletions=false)");
        }
    }
}
