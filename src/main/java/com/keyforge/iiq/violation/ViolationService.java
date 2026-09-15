package com.keyforge.iiq.violation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves PolicyViolations from IdentityIQ's SCIM v2 API ({@code GET /scim/v2/PolicyViolations},
 * verified advertised). Standard SCIM ListResponse pagination. On this instance the source
 * currently has zero violations — a valid empty result, not a failure.
 */
public class ViolationService {

    static final String VIOLATIONS_PATH = "scim/v2/PolicyViolations";
    static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqApiClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ViolationService(IiqApiClient client) {
        this.client = client;
    }

    public List<PolicyViolation> getAllViolations() {
        List<PolicyViolation> violations = new ArrayList<>();
        int startIndex = 1;
        int totalResults = Integer.MAX_VALUE;
        int pageCount = 0;

        while (violations.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + VIOLATIONS_PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            JsonNode root = parse(client.get(VIOLATIONS_PATH, query));
            totalResults = root.path("totalResults").asInt(0);
            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                break;
            }
            for (JsonNode node : resources) {
                violations.add(toViolation(node));
            }
            startIndex += resources.size();
        }
        return violations;
    }

    private PolicyViolation toViolation(JsonNode node) {
        return new PolicyViolation(
                text(node, "id"),
                text(node, "policyName"),
                text(node, "constraintName"),
                text(node, "status"),
                text(node, "description"),
                parseRef(node.path("owner")),
                parseRef(node.path("identity")));
    }

    private static PolicyViolation.Ref parseRef(JsonNode ref) {
        if (ref == null || ref.isMissingNode() || ref.isNull() || !ref.isObject()) {
            return null;
        }
        return new PolicyViolation.Ref(text(ref, "value"), text(ref, "$ref"), text(ref, "displayName"));
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + VIOLATIONS_PATH, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
