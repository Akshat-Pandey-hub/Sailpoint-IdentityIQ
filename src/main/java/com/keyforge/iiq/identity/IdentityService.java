package com.keyforge.iiq.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.Identity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieves Identity ({@code User}) records from IdentityIQ's SCIM v2 API.
 *
 * <p>The only endpoint used is {@code GET /scim/v2/Users}, which returns a SCIM
 * {@code ListResponse}. The service walks the full result set using SCIM
 * pagination ({@code startIndex} / {@code count} / {@code totalResults}) so that
 * every Identity is retrieved regardless of how many pages the server returns.
 */
public class IdentityService {

    /** SCIM Users collection endpoint, relative to the configured base URL. */
    static final String USERS_PATH = "scim/v2/Users";

    /**
     * Page size requested from the server. The server may return fewer per page
     * (or ignore it and return everything at once); the pagination loop handles
     * both cases by advancing on the number of resources actually returned.
     */
    static final int PAGE_SIZE = 100;

    /** Backstop against a misbehaving server to avoid an unbounded loop. */
    private static final int MAX_PAGES = 10_000;

    /**
     * Only the fields fully captured in dedicated columns are excluded from
     * {@code extendedAttributes}. Everything else the User returns — including the
     * complete {@code name}, {@code emails}, {@code active}, {@code schemas},
     * {@code meta}, and any field we do not model — is preserved verbatim so nothing
     * is lost. (Column-mapped derivatives like firstname/status still exist too.)
     */
    private static final Set<String> FULLY_CAPTURED_COLUMN_FIELDS = Set.of(
            "id", "userName", "displayName");

    private final IiqApiClient client;
    private final ObjectMapper mapper;

    public IdentityService(IiqApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /**
     * Retrieves every Identity from IdentityIQ, following pagination.
     *
     * @return all Identities (never null; empty if the instance has none)
     * @throws IiqApiException if a request fails or a response cannot be parsed
     */
    public List<Identity> getAllIdentities() {
        List<Identity> identities = new ArrayList<>();

        int startIndex = 1;          // SCIM is 1-based
        int totalResults = Integer.MAX_VALUE; // unknown until the first response
        int pageCount = 0;

        while (identities.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException(
                        "Aborting after " + MAX_PAGES + " pages; the server may not be honouring pagination.");
            }

            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            String body = client.get(USERS_PATH, query);
            JsonNode root = parse(body);

            // Update the authoritative total from every page (avoids hardcoding a count).
            totalResults = root.path("totalResults").asInt(0);

            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                // No (more) results — stop even if totalResults claimed otherwise.
                break;
            }

            for (JsonNode userNode : resources) {
                identities.add(toIdentity(userNode));
            }

            // Advance by the number of resources actually returned on this page.
            startIndex += resources.size();
        }

        return identities;
    }

    // --- parsing ------------------------------------------------------------

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + USERS_PATH, e);
        }
    }

    /** Maps a single SCIM User resource node into an {@link Identity}. */
    private Identity toIdentity(JsonNode user) {
        String id = text(user, "id");
        String userName = text(user, "userName");
        String displayName = text(user, "displayName");
        Boolean active = user.hasNonNull("active") ? user.get("active").asBoolean() : null;

        String email = primaryEmail(user.path("emails"));
        String firstName = text(user.path("name"), "givenName");
        String lastName = text(user.path("name"), "familyName");

        List<JsonNode> accountReferences = collectAccountReferences(user);
        List<JsonNode> entitlementReferences = collectEntitlementReferences(user);
        ObjectNode extendedAttributes = collectExtendedAttributes(user);

        return new Identity(id, userName, displayName, active, email, firstName, lastName,
                accountReferences, entitlementReferences, extendedAttributes);
    }

    /**
     * Preserves everything on the User beyond the mapped/structural fields, keeping
     * the original JSON structure intact (arrays, nested objects, ...). Generic:
     * it does not enumerate custom attribute names.
     */
    private static ObjectNode collectExtendedAttributes(JsonNode user) {
        if (!user.isObject()) {
            return null;
        }
        ObjectNode copy = user.deepCopy();
        copy.remove(FULLY_CAPTURED_COLUMN_FIELDS);
        return copy;
    }

    /**
     * Picks an email from the SCIM multi-valued {@code emails} attribute: the
     * one flagged {@code primary}, else the first one present.
     */
    private static String primaryEmail(JsonNode emails) {
        if (!emails.isArray() || emails.isEmpty()) {
            return null;
        }
        String firstValue = null;
        for (JsonNode email : emails) {
            String value = text(email, "value");
            if (firstValue == null) {
                firstValue = value;
            }
            if (email.path("primary").asBoolean(false)) {
                return value;
            }
        }
        return firstValue;
    }

    /**
     * Preserves account references without inventing a schema we have not
     * confirmed. Any array-valued attribute whose name contains "account"
     * (case-insensitive) is collected, including such attributes nested one
     * level deep inside SCIM extension objects (e.g. the SailPoint extension
     * namespace). If none are present, the result is empty.
     */
    private static List<JsonNode> collectAccountReferences(JsonNode user) {
        List<JsonNode> refs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = user.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            if (isAccountKey(key) && value.isArray()) {
                value.forEach(refs::add);
            } else if (value.isObject()) {
                // Descend one level into extension containers.
                Iterator<Map.Entry<String, JsonNode>> subFields = value.fields();
                while (subFields.hasNext()) {
                    Map.Entry<String, JsonNode> sub = subFields.next();
                    if (isAccountKey(sub.getKey()) && sub.getValue().isArray()) {
                        sub.getValue().forEach(refs::add);
                    }
                }
            }
        }
        return refs;
    }

    private static boolean isAccountKey(String key) {
        return key != null && key.toLowerCase().contains("account");
    }

    /**
     * Preserves the raw entitlement references exposed by the SailPoint User
     * extension. Generic: collects any {@code entitlements} array found at the top
     * level (SCIM core) or one level deep inside an extension object, without
     * hardcoding the extension namespace. If none are present, the result is empty.
     */
    private static List<JsonNode> collectEntitlementReferences(JsonNode user) {
        List<JsonNode> refs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = user.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            if (isEntitlementsKey(key) && value.isArray()) {
                value.forEach(refs::add);
            } else if (value.isObject()) {
                // Descend one level into extension containers (e.g. the SailPoint User extension).
                JsonNode nested = value.get("entitlements");
                if (nested != null && nested.isArray()) {
                    nested.forEach(refs::add);
                }
            }
        }
        return refs;
    }

    private static boolean isEntitlementsKey(String key) {
        return "entitlements".equalsIgnoreCase(key);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
