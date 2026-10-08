package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * KF Agent REST read service for native Access-History identity events
 * ({@code sailpoint.object.accesshistory.HistoricalIdentityEvent} → {@code kf_access_hist_identity_event}).
 * Same architecture as {@link NativeAccessHistoryEntitlementRestService}: Access-History has no
 * Record/Parser/Sink seam and {@link NativeAccessHistoryImportService} is DB-coupled, so the REST path reuses
 * the DB-free {@link NativeAccessHistoryClient} + the {@code {sourceCount, rows:[…]}} envelope and maps each
 * {@code JsonNode} row with the SAME wire keys {@link NativeHistIdentityEventRepository#append} reads. No
 * import service, no repository, no {@link java.sql.Connection} on the REST path.
 *
 * <p><b>REST contract</b> (verified from {@link NativeHistIdentityEventRepository#append} +
 * {@code kf_access_hist_identity_event}): 38 SailPoint-facing fields. {@code event_detail_json} is the only
 * structured field — a {@code text} column holding the raw event-detail payload; it is re-hydrated to its
 * structured JSON form and is NOT filterable. {@code event_date}/{@code created_at}/{@code modified_at} are
 * ISO-8601 timestamps ({@code modified_at} is genuinely nullable); every other field is text, including
 * {@code qry_property1}..{@code qry_property10} (the native event's queryable history properties) which are
 * preserved as real columns. There are no boolean fields. The 37 scalar fields are filterable. The KeyForge
 * {@code hist_event_id} PK, {@code record_hash} and the lineage envelope are excluded. Append-only (no
 * soft-delete columns). No native {@code modifiedAfter} (full-scan only).
 */
public final class NativeAccessHistoryIdentityEventRestService {

    /** Transport seam: pages the {@code {sourceCount, rows:[…]}} envelope (client in prod, a fake in tests). */
    @FunctionalInterface
    public interface PageSource {
        String fetchPage(int start, int limit);
    }

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> wire reader. The {@code event_detail_json} structured field is NOT filterable. */
    private static final Map<String, Function<JsonNode, String>> SCALARS = buildScalars();

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

        List<JsonNode> all = collectAll(source);

        List<JsonNode> matched = new ArrayList<>();
        for (JsonNode r : all) {
            if (matches(r, filters)) {
                matched.add(r);
            }
        }

        int from = (start == null || start < 0) ? 0 : start;
        if (from > matched.size()) from = matched.size();
        int to = (limit == null || limit < 0) ? matched.size() : Math.min(matched.size(), from + limit);
        if (to < from) to = from;

        List<Map<String, Object>> out = new ArrayList<>(to - from);
        for (JsonNode r : matched.subList(from, to)) {
            out.add(toJson(r));
        }
        return out;
    }

    private static boolean matches(JsonNode r, Map<String, String> filters) {
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
     * Walks the native source with the SAME page→parse loop the import service uses, minus the JDBC append
     * (DB bypass): page → parse {@code {sourceCount, rows:[…]}} envelope → collect the row nodes.
     */
    private List<JsonNode> collectAll(PageSource source) {
        List<JsonNode> all = new ArrayList<>();
        int start = 0;
        while (true) {
            JsonNode envelope = parse(source.fetchPage(start, INTERNAL_PAGE_SIZE));
            JsonNode err = envelope.get("error");
            if (err != null && !err.isNull()) {
                throw new NativeImportException("Access-History identity-event endpoint returned an error: "
                        + err.path("type").asText("unknown") + ": " + err.path("message").asText(""));
            }
            JsonNode rows = envelope.path("rows");
            if (!rows.isArray() || rows.size() == 0) {
                break;
            }
            for (JsonNode row : rows) {
                all.add(row);
            }
            if (rows.size() < INTERNAL_PAGE_SIZE) {
                break;
            }
            start += INTERNAL_PAGE_SIZE;
        }
        return all;
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new NativeImportException("Empty Access-History identity-event response payload");
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new NativeImportException("Unexpected Access-History identity-event response (not a JSON object)");
            }
            return root;
        } catch (NativeImportException e) {
            throw e;
        } catch (Exception e) {
            throw new NativeImportException("Unparseable Access-History identity-event response: " + e.getMessage(), e);
        }
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_access_hist_identity_event order). */
    private Map<String, Object> toJson(JsonNode r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", NativeJsonRead.text(r, "sourceId"));
        m.put("name", NativeJsonRead.text(r, "name"));
        m.put("entity_id", NativeJsonRead.text(r, "entityId"));
        m.put("entity_name", NativeJsonRead.text(r, "entityName"));
        m.put("defined_entity_name", NativeJsonRead.text(r, "definedEntityName"));
        m.put("event_type", NativeJsonRead.text(r, "eventType"));
        m.put("event_category", NativeJsonRead.text(r, "eventCategory"));
        m.put("event_source_type", NativeJsonRead.text(r, "eventSourceType"));
        m.put("event_date", iso(NativeJsonRead.instant(r, "eventDate")));
        m.put("event_detail_json", jsonField(r, "eventDetailJson"));   // structured event-detail payload
        m.put("prev_capture_id", NativeJsonRead.text(r, "prevCaptureId"));
        m.put("capture_id", NativeJsonRead.text(r, "captureId"));
        m.put("audit_event_id", NativeJsonRead.text(r, "auditEventId"));
        m.put("property_name", NativeJsonRead.text(r, "propertyName"));
        m.put("old_value", NativeJsonRead.text(r, "oldValue"));
        m.put("new_value", NativeJsonRead.text(r, "newValue"));
        m.put("account_id", NativeJsonRead.text(r, "accountId"));
        m.put("acct_app_id", NativeJsonRead.text(r, "acctAppId"));
        m.put("acct_app_name", NativeJsonRead.text(r, "acctAppName"));
        m.put("acct_app_instance", NativeJsonRead.text(r, "acctAppInstance"));
        m.put("acct_display_name", NativeJsonRead.text(r, "acctDisplayName"));
        m.put("acct_native_id", NativeJsonRead.text(r, "acctNativeId"));
        m.put("pending_request_item_id", NativeJsonRead.text(r, "pendingRequestItemId"));
        m.put("request_item_id", NativeJsonRead.text(r, "requestItemId"));
        m.put("identity_request_id", NativeJsonRead.text(r, "identityRequestId"));
        m.put("identity_entitlement_id", NativeJsonRead.text(r, "identityEntitlementId"));
        for (int q = 1; q <= 10; q++) {
            m.put("qry_property" + q, NativeJsonRead.text(r, "qryProperty" + q));
        }
        m.put("created_at", iso(NativeJsonRead.instant(r, "created")));
        m.put("modified_at", iso(NativeJsonRead.instant(r, "modified")));
        return m;
    }

    private static Map<String, Function<JsonNode, String>> buildScalars() {
        Map<String, Function<JsonNode, String>> m = new LinkedHashMap<>();
        m.put("source_id", r -> NativeJsonRead.text(r, "sourceId"));
        m.put("name", r -> NativeJsonRead.text(r, "name"));
        m.put("entity_id", r -> NativeJsonRead.text(r, "entityId"));
        m.put("entity_name", r -> NativeJsonRead.text(r, "entityName"));
        m.put("defined_entity_name", r -> NativeJsonRead.text(r, "definedEntityName"));
        m.put("event_type", r -> NativeJsonRead.text(r, "eventType"));
        m.put("event_category", r -> NativeJsonRead.text(r, "eventCategory"));
        m.put("event_source_type", r -> NativeJsonRead.text(r, "eventSourceType"));
        m.put("event_date", r -> iso(NativeJsonRead.instant(r, "eventDate")));
        m.put("prev_capture_id", r -> NativeJsonRead.text(r, "prevCaptureId"));
        m.put("capture_id", r -> NativeJsonRead.text(r, "captureId"));
        m.put("audit_event_id", r -> NativeJsonRead.text(r, "auditEventId"));
        m.put("property_name", r -> NativeJsonRead.text(r, "propertyName"));
        m.put("old_value", r -> NativeJsonRead.text(r, "oldValue"));
        m.put("new_value", r -> NativeJsonRead.text(r, "newValue"));
        m.put("account_id", r -> NativeJsonRead.text(r, "accountId"));
        m.put("acct_app_id", r -> NativeJsonRead.text(r, "acctAppId"));
        m.put("acct_app_name", r -> NativeJsonRead.text(r, "acctAppName"));
        m.put("acct_app_instance", r -> NativeJsonRead.text(r, "acctAppInstance"));
        m.put("acct_display_name", r -> NativeJsonRead.text(r, "acctDisplayName"));
        m.put("acct_native_id", r -> NativeJsonRead.text(r, "acctNativeId"));
        m.put("pending_request_item_id", r -> NativeJsonRead.text(r, "pendingRequestItemId"));
        m.put("request_item_id", r -> NativeJsonRead.text(r, "requestItemId"));
        m.put("identity_request_id", r -> NativeJsonRead.text(r, "identityRequestId"));
        m.put("identity_entitlement_id", r -> NativeJsonRead.text(r, "identityEntitlementId"));
        for (int q = 1; q <= 10; q++) {
            final String wire = "qryProperty" + q;
            m.put("qry_property" + q, r -> NativeJsonRead.text(r, wire));
        }
        m.put("created_at", r -> iso(NativeJsonRead.instant(r, "created")));
        m.put("modified_at", r -> iso(NativeJsonRead.instant(r, "modified")));
        return m;
    }

    /**
     * Return a structured JSON value for {@code key}: a wire node that is already an object/array is used
     * directly; a JSON-string (e.g. {@code event_detail_json}, serialized by the plugin) is re-hydrated into
     * its JSON form so the REST response carries structured JSON rather than an escaped string. null/empty
     * stays null.
     */
    private Object jsonField(JsonNode r, String key) {
        JsonNode n = (r == null) ? null : r.get(key);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            String s = n.asText();
            if (s.isEmpty()) {
                return null;
            }
            try {
                JsonNode parsed = mapper.readTree(s);
                return (parsed == null || parsed.isNull()) ? null : parsed;
            } catch (Exception e) {
                return s;
            }
        }
        return n;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
