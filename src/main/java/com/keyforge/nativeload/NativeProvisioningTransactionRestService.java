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
 * KF Agent REST read service for native provisioning transactions ({@code sailpoint.object.ProvisioningTransaction}
 * → {@code kf_provisioning_txn}). This reuses the EXISTING native extraction verbatim: the same
 * {@link NativeProvisioningTxnClient} page source + {@link NativeProvisioningTxnImportService} that the DB path
 * uses, with a non-JDBC {@link CollectingSink} that collects only the TRANSACTION rows (no-ops the derived item
 * upserts) so no PostgreSQL is touched and the shared Provisioning-Item derivation is left intact.
 *
 * <p><b>REST contract</b> (verified from {@link NativeProvisioningTxnRecord} + {@link NativeProvisioningTxnParser}
 * + {@code kf_provisioning_txn}): 34 SailPoint-facing fields in DB business-field order. {@code plan_result_errors}
 * is the only {@code jsonb} field (returned as JSON); {@code forced}/{@code timed_out}/{@code filtered} are
 * booleans; {@code retry_count}/{@code item_count} are integers; {@code last_retry}/{@code created_at}/
 * {@code modified_at} are ISO-8601 timestamps; every other field is {@code text}. The jsonb field is not
 * filterable; the other 33 are scalar and filterable. The KeyForge {@code provisioningtxnid} PK,
 * {@code record_hash}, lineage envelope ({@code source_system}/{@code source_interface}/{@code source_object_type}/
 * {@code extraction_run_id}/{@code extracted_at}) and soft-delete columns are excluded. No native
 * {@code modifiedAfter} (the txn client has no server-side modified filter → full-scan only).
 */
public final class NativeProvisioningTransactionRestService {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> value extractor. The {@code plan_result_errors} jsonb field is NOT filterable. */
    private static final Map<String, Function<NativeProvisioningTxnRecord, String>> SCALARS = buildScalars();

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

        List<NativeProvisioningTxnRecord> all = collectAll(source);

        List<NativeProvisioningTxnRecord> matched = new ArrayList<>();
        for (NativeProvisioningTxnRecord r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (NativeProvisioningTxnRecord r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(NativeProvisioningTxnRecord r, Map<String, String> filters) {
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
    private List<NativeProvisioningTxnRecord> collectAll(NativeProvisioningTxnPageSource source) {
        CollectingSink sink = new CollectingSink();
        try {
            // sweepDeletions=false -> the sink's sweep* methods are never invoked; no PostgreSQL is touched.
            new NativeProvisioningTxnImportService(source, sink, INTERNAL_PAGE_SIZE, false).importAll();
        } catch (SQLException e) {
            throw new NativeImportException("native provisioning-transaction extraction failed: " + e.getMessage(), e);
        }
        return sink.txns;
    }

    /** The SailPoint-facing transaction fields, keyed by our DB column names (kf_provisioning_txn order). */
    private Map<String, Object> toJson(NativeProvisioningTxnRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", r.sourceId);
        m.put("name", r.name);
        m.put("operation", r.operation);
        m.put("type", r.type);
        m.put("status", r.status);
        m.put("source", r.source);
        m.put("integration", r.integration);
        m.put("forced", r.forced);
        m.put("identity_name", r.identityName);
        m.put("identity_display_name", r.identityDisplayName);
        m.put("application_name", r.applicationName);
        m.put("native_identity", r.nativeIdentity);
        m.put("account_display_name", r.accountDisplayName);
        m.put("certification_id", r.certificationId);
        m.put("certification_name", r.certificationName);
        m.put("access_request_id", r.accessRequestId);
        m.put("wait_work_item_id", r.waitWorkItemId);
        m.put("manual_work_item_id", r.manualWorkItemId);
        m.put("ticket_id", r.ticketId);
        m.put("retry_count", r.retryCount);
        m.put("timed_out", r.timedOut);
        m.put("filtered", r.filtered);
        m.put("retry_request_id", r.retryRequestId);
        m.put("last_retry", iso(r.lastRetry));
        m.put("plan_result_status", r.planResultStatus);
        m.put("plan_result_request_id", r.planResultRequestId);
        m.put("plan_result_errors", node(r.planResultErrorsJson));   // jsonb
        m.put("account_request_operation", r.accountRequestOperation);
        m.put("request_id", r.requestId);
        m.put("item_count", r.itemCount);
        m.put("owner_id", r.ownerId);
        m.put("owner_name", r.ownerName);
        m.put("created_at", iso(r.created));
        m.put("modified_at", iso(r.modified));
        return m;
    }

    private static Map<String, Function<NativeProvisioningTxnRecord, String>> buildScalars() {
        Map<String, Function<NativeProvisioningTxnRecord, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> r.sourceId);
        m.put("name", r -> r.name);
        m.put("operation", r -> r.operation);
        m.put("type", r -> r.type);
        m.put("status", r -> r.status);
        m.put("source", r -> r.source);
        m.put("integration", r -> r.integration);
        m.put("forced", r -> r.forced == null ? null : r.forced.toString());
        m.put("identity_name", r -> r.identityName);
        m.put("identity_display_name", r -> r.identityDisplayName);
        m.put("application_name", r -> r.applicationName);
        m.put("native_identity", r -> r.nativeIdentity);
        m.put("account_display_name", r -> r.accountDisplayName);
        m.put("certification_id", r -> r.certificationId);
        m.put("certification_name", r -> r.certificationName);
        m.put("access_request_id", r -> r.accessRequestId);
        m.put("wait_work_item_id", r -> r.waitWorkItemId);
        m.put("manual_work_item_id", r -> r.manualWorkItemId);
        m.put("ticket_id", r -> r.ticketId);
        m.put("retry_count", r -> r.retryCount == null ? null : String.valueOf(r.retryCount));
        m.put("timed_out", r -> r.timedOut == null ? null : r.timedOut.toString());
        m.put("filtered", r -> r.filtered == null ? null : r.filtered.toString());
        m.put("retry_request_id", r -> r.retryRequestId);
        m.put("last_retry", r -> iso(r.lastRetry));
        m.put("plan_result_status", r -> r.planResultStatus);
        m.put("plan_result_request_id", r -> r.planResultRequestId);
        m.put("account_request_operation", r -> r.accountRequestOperation);
        m.put("request_id", r -> r.requestId);
        m.put("item_count", r -> r.itemCount == null ? null : String.valueOf(r.itemCount));
        m.put("owner_id", r -> r.ownerId);
        m.put("owner_name", r -> r.ownerName);
        m.put("created_at", r -> iso(r.created));
        m.put("modified_at", r -> iso(r.modified));
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

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }

    /** In-memory sink: collects only transaction rows; item upserts + sweeps are no-ops (no DB, item derivation intact). */
    private static final class CollectingSink implements NativeProvisioningTxnSink {
        final List<NativeProvisioningTxnRecord> txns = new ArrayList<>();

        @Override
        public void ensure() {
            // no-op: no table, no PostgreSQL
        }

        @Override
        public NativeProvisioningTxnRepository.UpsertOutcome upsertTxn(NativeProvisioningTxnRecord record) {
            txns.add(record);
            return NativeProvisioningTxnRepository.UpsertOutcome.INSERTED;
        }

        @Override
        public NativeProvisioningItemRepository.UpsertOutcome upsertItem(NativeProvisioningItemRecord record) {
            // the derived item is a separate entity (/kfagent/provisioning-items); the txn endpoint ignores it.
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
