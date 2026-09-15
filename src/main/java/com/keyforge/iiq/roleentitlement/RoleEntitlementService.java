package com.keyforge.iiq.roleentitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves a Role/Bundle's <b>direct</b> entitlements (Bundle → Profile → Entitlement) from
 * the IdentityIQ Role modeler, using the exact request the Role viewer's "Direct Entitlements"
 * grid makes (verified live): {@code GET define/roles/modeler/readOnlySimpleEntitlementsJSON.json}
 * with the {@code roleId} parameter. IIQ resolves the profile constraints into concrete
 * entitlements itself; this service only reads them. It uses the existing {@link IiqSessionClient}
 * (this is a classic UI endpoint that needs a web session, not Basic auth).
 *
 * <p>Only a role's OWN direct entitlements are read — never the entitlements of required/permitted
 * or inherited roles (that is role→role hierarchy, out of scope for this relationship).
 */
public class RoleEntitlementService {

    static final String SIMPLE_ENTITLEMENTS_PATH = "define/roles/modeler/readOnlySimpleEntitlementsJSON.json";
    static final String ENTITLEMENTS_ARRAY_KEY = "entitlements";
    static final String TOTAL_KEY = "numEntitlements";
    static final int PAGE_LIMIT = 25;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public RoleEntitlementService(IiqSessionClient client) {
        this.client = client;
    }

    /** Direct entitlements granted by one role (may be empty; never null). */
    public List<RoleEntitlementGrant> fetchGrantsFor(String roleId) {
        List<RoleEntitlementGrant> grants = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (grants.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " entitlement pages for role " + roleId);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("roleId", roleId);
            query.put("start", Integer.toString(start));
            query.put("limit", Integer.toString(PAGE_LIMIT));
            query.put("page", Integer.toString(start / PAGE_LIMIT + 1));
            query.put("_dc", Long.toString(System.currentTimeMillis()));

            String body = client.get(SIMPLE_ENTITLEMENTS_PATH, query);
            List<RoleEntitlementGrant> page = parseGrants(body, roleId);
            int declaredTotal = parseTotal(body);
            if (page.isEmpty()) {
                break;
            }
            grants.addAll(page);
            if (declaredTotal < 0) {
                break;
            }
            total = declaredTotal;
            start += page.size();
        }
        return grants;
    }

    // --- pure parsing (unit-testable) ---------------------------------------

    /** Parses the direct-entitlements grid JSON into grants for the given role. */
    public List<RoleEntitlementGrant> parseGrants(String json, String roleId) {
        List<RoleEntitlementGrant> grants = new ArrayList<>();
        JsonNode root = parse(json);
        JsonNode array = root.get(ENTITLEMENTS_ARRAY_KEY);
        if (array != null && array.isArray()) {
            for (JsonNode e : array) {
                grants.add(new RoleEntitlementGrant(
                        roleId,
                        text(e, "applicationName"),
                        text(e, "property"),
                        text(e, "value"),
                        text(e, "displayValue"),
                        text(e, "classifications")));
            }
        }
        return grants;
    }

    int parseTotal(String json) {
        JsonNode root = parse(json);
        JsonNode v = root.get(TOTAL_KEY);
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
            throw new IiqApiException("Failed to parse role entitlements response from " + SIMPLE_ENTITLEMENTS_PATH, e);
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
