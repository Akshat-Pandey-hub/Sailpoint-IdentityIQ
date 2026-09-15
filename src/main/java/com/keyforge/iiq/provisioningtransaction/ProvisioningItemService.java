package com.keyforge.iiq.provisioningtransaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts provisioning <b>items</b> (plan lines) from IdentityIQ. The list endpoint
 * {@code GET /rest/provisioningTransactions} returns the item arrays empty; the per-transaction
 * <b>detail</b> route {@code GET /rest/provisioningTransactions/{id}} populates them (verified live:
 * 251 attributeRequests + 10 filteredRequests across 103 transactions). So this service pages the
 * list to collect transaction ids, then reads each transaction's detail and flattens its
 * {@code attributeRequests}, {@code permissionRequests}, and {@code filteredRequests} arrays into
 * {@link ProvisioningItem}s tagged with their parent transaction id.
 *
 * <p>Same verified interface and session ({@link IiqSessionClient}) as
 * {@link ProvisioningTransactionService}; the CSRF token is warmed once. Extraction-only.
 */
public class ProvisioningItemService {

    static final String PATH = "rest/provisioningTransactions";
    static final String ROOT_KEY = "objects";
    static final String TOTAL_KEY = "count";
    static final int PAGE_LIMIT = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProvisioningItemService(IiqSessionClient client) {
        this.client = client;
    }

    /** Reads every provisioning item by fetching each transaction's detail plan. */
    public List<ProvisioningItem> getAllItems() {
        client.warmCsrfToken(); // detail route requires the CSRF token
        List<ProvisioningItem> out = new ArrayList<>();
        for (String id : getAllTransactionIds()) {
            String detail = client.get(PATH + "/" + id, null);
            out.addAll(parseItems(detail));
        }
        return out;
    }

    /** Pages the list endpoint and collects the transaction ids only. */
    public List<String> getAllTransactionIds() {
        List<String> ids = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;
        while (ids.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("start", Integer.toString(start));
            query.put("limit", Integer.toString(PAGE_LIMIT));
            String body = client.get(PATH, query);
            JsonNode root = parse(body);
            JsonNode arr = root.get(ROOT_KEY);
            int declaredTotal = root.get(TOTAL_KEY) != null && root.get(TOTAL_KEY).isNumber()
                    ? root.get(TOTAL_KEY).asInt() : -1;
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                break;
            }
            int before = ids.size();
            for (JsonNode t : arr) {
                String id = text(t, "id");
                if (id != null) {
                    ids.add(id);
                }
            }
            if (declaredTotal < 0) {
                break;
            }
            total = declaredTotal;
            start += (ids.size() - before);
        }
        return ids;
    }

    // --- pure parsing (unit-testable; use with a null client) ---------------

    /** Flattens a single transaction detail's three plan arrays into items tagged with the parent id. */
    public List<ProvisioningItem> parseItems(String detailJson) {
        JsonNode root = parse(detailJson);
        String parentId = text(root, "id");
        List<ProvisioningItem> items = new ArrayList<>();
        addArray(items, parentId, "attribute", root.path("attributeRequests"));
        addArray(items, parentId, "permission", root.path("permissionRequests"));
        addArray(items, parentId, "filtered", root.path("filteredRequests"));
        return items;
    }

    private void addArray(List<ProvisioningItem> items, String parentId, String requestType, JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode it : arr) {
            JsonNode errs = it.path("errorMessages");
            items.add(new ProvisioningItem(
                    parentId,
                    requestType,
                    index++,
                    text(it, "operation"),
                    text(it, "name"),
                    text(it, "value"),
                    text(it, "result"),
                    text(it, "reason"),
                    bool(it, "attributeRequest"),
                    errs.isArray() ? errs.toString() : null));
        }
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse provisioningTransactions response from " + PATH, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.asBoolean() : null;
    }
}
