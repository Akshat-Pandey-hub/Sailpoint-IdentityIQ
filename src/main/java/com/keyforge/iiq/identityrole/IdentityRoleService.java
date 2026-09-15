package com.keyforge.iiq.identityrole;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts the authoritative Identity&rarr;Role assignment relationship. IdentityIQ does not expose
 * assigned roles through SCIM {@code /Users} (its SailPoint extension omits roles), so this service
 * uses SCIM only to enumerate identity ids, then reads each identity's own persisted assignments from
 * the classic REST endpoint {@code GET /rest/identities/{id}} &rarr; {@code assignedRoles[]} (HTTP
 * Basic, verified live). The relationship is taken from explicit source ids (identity id + role id) —
 * never inferred from role definitions, entitlements, accounts, or access-request runtime data.
 * Extraction-only.
 */
public class IdentityRoleService {

    static final String USERS_PATH = "scim/v2/Users";
    static final String IDENTITY_DETAIL_PREFIX = "rest/identities/";
    static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqApiClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public IdentityRoleService(IiqApiClient client) {
        this.client = client;
    }

    /** Reads every identity's assigned roles, one identity detail call at a time. */
    public List<IdentityRoleAssignment> getAllAssignments() {
        List<IdentityRoleAssignment> out = new ArrayList<>();
        for (String id : getAllIdentityIds()) {
            String detail = client.get(IDENTITY_DETAIL_PREFIX + id, null);
            out.addAll(parseAssignments(id, detail));
        }
        return out;
    }

    /** Enumerates identity ids via SCIM {@code /Users} (id only). */
    public List<String> getAllIdentityIds() {
        List<String> ids = new ArrayList<>();
        int startIndex = 1;
        int totalResults = Integer.MAX_VALUE;
        int pageCount = 0;
        while (ids.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + USERS_PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));
            JsonNode root = parse(client.get(USERS_PATH, query));
            totalResults = root.path("totalResults").asInt(0);
            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                break;
            }
            for (JsonNode u : resources) {
                String id = text(u, "id");
                if (id != null) {
                    ids.add(id);
                }
            }
            startIndex += resources.size();
        }
        return ids;
    }

    // --- pure parsing (unit-testable; use with a null client) ---------------

    /** Parses one identity detail's {@code assignedRoles[]} into assignments tagged with the identity id. */
    public List<IdentityRoleAssignment> parseAssignments(String identityId, String detailJson) {
        List<IdentityRoleAssignment> out = new ArrayList<>();
        JsonNode arr = parse(detailJson).path("assignedRoles");
        if (arr.isArray()) {
            for (JsonNode a : arr) {
                out.add(new IdentityRoleAssignment(
                        identityId,
                        text(a, "id"),
                        text(a, "displayName"),
                        epochMillis(a, "date"),
                        text(a, "assigner"),
                        text(a, "description")));
            }
        }
        return out;
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse identity/role response", e);
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

    private static Long epochMillis(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asLong() : null;
    }
}
