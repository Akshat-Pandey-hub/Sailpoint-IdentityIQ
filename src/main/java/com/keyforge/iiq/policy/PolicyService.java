package com.keyforge.iiq.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves Policy definitions from the IdentityIQ Policy management grid
 * ({@code define/policy/policiesDataSource.json}) — a classic UI datasource that needs a web
 * session, and whose store issues a {@code POST} (verified from the page). Envelope:
 * {@code {"objects":[...],"count":N,"success":true}}. Only the authoritative grid fields
 * (id/name/type/state/description) are read. On this instance the source currently has zero
 * policies — a valid empty result.
 */
public class PolicyService {

    static final String POLICIES_PATH = "define/policy/policiesDataSource.json";
    static final String OBJECTS_KEY = "objects";
    static final String TOTAL_KEY = "count";
    static final int PAGE_LIMIT = 25;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public PolicyService(IiqSessionClient client) {
        this.client = client;
    }

    public List<PolicyDefinition> getAllPolicies() {
        List<PolicyDefinition> policies = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (policies.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + POLICIES_PATH);
            }
            Map<String, String> params = new LinkedHashMap<>();
            params.put("start", Integer.toString(start));
            params.put("limit", Integer.toString(PAGE_LIMIT));
            params.put("page", Integer.toString(start / PAGE_LIMIT + 1));
            params.put("_dc", Long.toString(System.currentTimeMillis()));

            // The Policy grid store uses POST (verified); postForm sends the form body and returns JSON.
            String body = client.postForm(POLICIES_PATH, params);
            List<PolicyDefinition> page = parsePolicies(body);
            int declaredTotal = parseTotal(body);
            if (page.isEmpty()) {
                break;
            }
            policies.addAll(page);
            if (declaredTotal < 0) {
                break;
            }
            total = declaredTotal;
            start += page.size();
        }
        return policies;
    }

    // --- pure parsing (unit-testable) ---------------------------------------

    public List<PolicyDefinition> parsePolicies(String json) {
        List<PolicyDefinition> policies = new ArrayList<>();
        JsonNode root = parse(json);
        JsonNode array = root.get(OBJECTS_KEY);
        if (array != null && array.isArray()) {
            for (JsonNode p : array) {
                policies.add(new PolicyDefinition(
                        text(p, "id"), text(p, "name"), text(p, "type"),
                        text(p, "state"), text(p, "description")));
            }
        }
        return policies;
    }

    int parseTotal(String json) {
        JsonNode v = parse(json).get(TOTAL_KEY);
        if (v != null && v.isNumber()) {
            return v.asInt();
        }
        if (v != null && v.isTextual()) {
            try {
                return Integer.parseInt(v.asText().trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse Policy response from " + POLICIES_PATH, e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
