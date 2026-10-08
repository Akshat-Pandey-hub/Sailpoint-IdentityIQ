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
 * KF Agent REST read service for native Access-History entitlement captures
 * ({@code sailpoint.object.accesshistory.HistoricalEntitlementCapture} → {@code kf_access_hist_entitlement}).
 * Reuses the EXISTING native extraction verbatim: the same {@link NativeAccessHistoryClient} transport and the
 * same {@code {sourceCount, rows:[…]}} envelope the DB path pages. Unlike most entities, Access-History has no
 * Record/Parser/Sink seam — {@link NativeAccessHistoryImportService} works directly on {@code JsonNode} rows
 * and is coupled to a JDBC {@link NativeAccessHistoryRepo} (it needs a {@link java.sql.Connection}) — so the
 * REST path cannot reuse the import service without touching PostgreSQL. It therefore replays the SAME
 * page→parse loop (client + envelope) into an in-memory list and maps each {@code JsonNode} row to the
 * response with the SAME wire keys the repository's {@code append} reads. No existing extraction or DB code is
 * modified.
 *
 * <p><b>REST contract</b> (verified from {@link NativeHistEntitlementCaptureRepository#append} +
 * {@code kf_access_hist_entitlement}): 41 SailPoint-facing fields. {@code compressible_property_names},
 * {@code capture_json}, {@code attributes} and {@code extended_attributes} are the structured ({@code jsonb} /
 * JSON) fields — returned as JSON, not filterable; {@code capture_json} (a {@code text} column holding the
 * full serialized capture payload) is re-hydrated to its structured JSON form. {@code granted_by_role}/
 * {@code deleted}/{@code latest}/{@code compressed}/{@code brief}/{@code full_capture}/{@code patch} are
 * booleans; {@code effective_date}/{@code extended_to_date}/{@code created_at}/{@code modified_at} are ISO-8601
 * timestamps; every other field is text ({@code source_id} is the capture's native source id). The three
 * compression-metadata fields ({@code compressed_property_flag}/{@code compressed_property_flag_value}/
 * {@code compressible_property_names}) are genuine native getters persisted as dedicated columns, so they are
 * part of the business contract and included. The 37 scalar fields are filterable. The KeyForge
 * {@code hist_capture_id} PK, {@code record_hash} and the lineage envelope ({@code source_system}/
 * {@code source_interface}/{@code source_object_type}/{@code extraction_run_id}/{@code extracted_at}) are
 * excluded. Append-only (no soft-delete columns). No native {@code modifiedAfter} (full-scan only).
 */
public final class NativeAccessHistoryEntitlementRestService {

    /** Transport seam: pages the {@code {sourceCount, rows:[…]}} envelope (client in prod, a fake in tests). */
    @FunctionalInterface
    public interface PageSource {
        String fetchPage(int start, int limit);
    }

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Scalar response field name -> wire reader. The four structured fields are NOT filterable. */
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
                throw new NativeImportException("Access-History entitlement endpoint returned an error: "
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
            throw new NativeImportException("Empty Access-History entitlement response payload");
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new NativeImportException("Unexpected Access-History entitlement response (not a JSON object)");
            }
            return root;
        } catch (NativeImportException e) {
            throw e;
        } catch (Exception e) {
            throw new NativeImportException("Unparseable Access-History entitlement response: " + e.getMessage(), e);
        }
    }

    /** The SailPoint-facing fields, keyed by our DB column names (kf_access_hist_entitlement order). */
    private Map<String, Object> toJson(JsonNode r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_id", NativeJsonRead.text(r, "sourceId"));
        m.put("name", NativeJsonRead.text(r, "name"));
        m.put("entity_id", NativeJsonRead.text(r, "entityId"));
        m.put("entity_name", NativeJsonRead.text(r, "entityName"));
        m.put("identity_id", NativeJsonRead.text(r, "identityId"));
        m.put("identity_name", NativeJsonRead.text(r, "identityName"));
        m.put("identity_entitlement_id", NativeJsonRead.text(r, "identityEntitlementId"));
        m.put("application_id", NativeJsonRead.text(r, "applicationId"));
        m.put("application_name", NativeJsonRead.text(r, "applicationName"));
        m.put("native_identity", NativeJsonRead.text(r, "nativeIdentity"));
        m.put("instance", NativeJsonRead.text(r, "instance"));
        m.put("display_value", NativeJsonRead.text(r, "displayValue"));
        m.put("attribute_name", NativeJsonRead.text(r, "attributeName"));
        m.put("attribute_value", NativeJsonRead.text(r, "attributeValue"));
        m.put("type", NativeJsonRead.text(r, "type"));
        m.put("granted_by_role", NativeJsonRead.bool(r, "grantedByRole"));           // boolean
        m.put("role_id", NativeJsonRead.text(r, "roleId"));
        m.put("request_item_id", NativeJsonRead.text(r, "requestItemId"));
        m.put("pending_request_item_id", NativeJsonRead.text(r, "pendingRequestItemId"));
        m.put("certification_item_id", NativeJsonRead.text(r, "certificationItemId"));
        m.put("deleted", NativeJsonRead.bool(r, "deleted"));                         // boolean
        m.put("effective_date", iso(NativeJsonRead.instant(r, "effectiveDate")));
        m.put("extended_to_date", iso(NativeJsonRead.instant(r, "extendedToDate")));
        m.put("latest", NativeJsonRead.bool(r, "latest"));                           // boolean
        m.put("compressed", NativeJsonRead.bool(r, "compressed"));                   // boolean
        m.put("brief", NativeJsonRead.bool(r, "brief"));                             // boolean
        m.put("full_capture", NativeJsonRead.bool(r, "full"));                       // boolean (wire key "full")
        m.put("patch", NativeJsonRead.bool(r, "patch"));                             // boolean
        m.put("smart_hash", NativeJsonRead.text(r, "smartHash"));
        m.put("full_hash", NativeJsonRead.text(r, "fullHash"));
        m.put("json_format", NativeJsonRead.text(r, "jsonFormat"));
        m.put("transform_type", NativeJsonRead.text(r, "transformType"));
        m.put("patch_doc_parent", NativeJsonRead.text(r, "patchDocParent"));
        m.put("compressed_property_flag", NativeJsonRead.text(r, "compressedPropertyFlag"));
        m.put("compressed_property_flag_value", NativeJsonRead.text(r, "compressedPropertyFlagValue"));
        m.put("compressible_property_names", jsonField(r, "compressiblePropertyNames"));   // jsonb
        m.put("capture_json", jsonField(r, "captureJson"));                                // structured capture payload
        m.put("attributes", jsonField(r, "attributes"));                                   // jsonb
        m.put("extended_attributes", jsonField(r, "extendedAttributes"));                  // jsonb
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
        m.put("identity_id", r -> NativeJsonRead.text(r, "identityId"));
        m.put("identity_name", r -> NativeJsonRead.text(r, "identityName"));
        m.put("identity_entitlement_id", r -> NativeJsonRead.text(r, "identityEntitlementId"));
        m.put("application_id", r -> NativeJsonRead.text(r, "applicationId"));
        m.put("application_name", r -> NativeJsonRead.text(r, "applicationName"));
        m.put("native_identity", r -> NativeJsonRead.text(r, "nativeIdentity"));
        m.put("instance", r -> NativeJsonRead.text(r, "instance"));
        m.put("display_value", r -> NativeJsonRead.text(r, "displayValue"));
        m.put("attribute_name", r -> NativeJsonRead.text(r, "attributeName"));
        m.put("attribute_value", r -> NativeJsonRead.text(r, "attributeValue"));
        m.put("type", r -> NativeJsonRead.text(r, "type"));
        m.put("granted_by_role", r -> str(NativeJsonRead.bool(r, "grantedByRole")));
        m.put("role_id", r -> NativeJsonRead.text(r, "roleId"));
        m.put("request_item_id", r -> NativeJsonRead.text(r, "requestItemId"));
        m.put("pending_request_item_id", r -> NativeJsonRead.text(r, "pendingRequestItemId"));
        m.put("certification_item_id", r -> NativeJsonRead.text(r, "certificationItemId"));
        m.put("deleted", r -> str(NativeJsonRead.bool(r, "deleted")));
        m.put("effective_date", r -> iso(NativeJsonRead.instant(r, "effectiveDate")));
        m.put("extended_to_date", r -> iso(NativeJsonRead.instant(r, "extendedToDate")));
        m.put("latest", r -> str(NativeJsonRead.bool(r, "latest")));
        m.put("compressed", r -> str(NativeJsonRead.bool(r, "compressed")));
        m.put("brief", r -> str(NativeJsonRead.bool(r, "brief")));
        m.put("full_capture", r -> str(NativeJsonRead.bool(r, "full")));
        m.put("patch", r -> str(NativeJsonRead.bool(r, "patch")));
        m.put("smart_hash", r -> NativeJsonRead.text(r, "smartHash"));
        m.put("full_hash", r -> NativeJsonRead.text(r, "fullHash"));
        m.put("json_format", r -> NativeJsonRead.text(r, "jsonFormat"));
        m.put("transform_type", r -> NativeJsonRead.text(r, "transformType"));
        m.put("patch_doc_parent", r -> NativeJsonRead.text(r, "patchDocParent"));
        m.put("compressed_property_flag", r -> NativeJsonRead.text(r, "compressedPropertyFlag"));
        m.put("compressed_property_flag_value", r -> NativeJsonRead.text(r, "compressedPropertyFlagValue"));
        m.put("created_at", r -> iso(NativeJsonRead.instant(r, "created")));
        m.put("modified_at", r -> iso(NativeJsonRead.instant(r, "modified")));
        return m;
    }

    /**
     * Return a structured JSON value for {@code key}: a wire node that is already an object/array is used
     * directly; a wire node that is a JSON-string (e.g. {@code capture_json}, serialized by the plugin) is
     * re-hydrated into its JSON form so the REST response carries structured JSON rather than an escaped
     * string. null/empty stays null.
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

    private static String str(Boolean b) {
        return b == null ? null : b.toString();
    }
}
