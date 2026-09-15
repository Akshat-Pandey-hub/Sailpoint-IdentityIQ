package com.keyforge.iiq.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.Account;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retrieves Account records from IdentityIQ's SCIM v2 API.
 *
 * <p>The only endpoint used is {@code GET /scim/v2/Accounts}, which returns a
 * SCIM {@code ListResponse}. Pagination follows the same proven strategy as the
 * Identity, Application and Entitlement services.
 *
 * <p>Because account attributes vary by Application, this service maps the
 * common/core fields into a typed {@link Account} and preserves every remaining
 * attribute verbatim (original JSON structure) so no information is lost.
 */
public class AccountService {

    /** SCIM Accounts collection endpoint, relative to the configured base URL. */
    static final String ACCOUNTS_PATH = "scim/v2/Accounts";

    /**
     * Page size requested from the server. The server may return fewer per page
     * (or return everything at once); the loop advances on the number of
     * resources actually returned so both cases are handled.
     */
    static final int PAGE_SIZE = 100;

    /** Backstop against a misbehaving server to avoid an unbounded loop. */
    private static final int MAX_PAGES = 10_000;

    /**
     * Core attribute names consumed into typed fields. Everything NOT in this set
     * is retained under {@link Account#getAdditionalAttributes()} in its original
     * JSON form.
     */
    private static final Set<String> CORE_FIELDS = Set.of(
            "id", "displayName", "nativeIdentity", "active", "manuallyCorrelated",
            "locked", "hasEntitlements", "lastRefresh", "application", "identity",
            "meta", "schemas");

    private final IiqApiClient client;
    private final ObjectMapper mapper;

    public AccountService(IiqApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /**
     * Retrieves every Account from IdentityIQ, following pagination.
     *
     * @return all Accounts (never null; empty if the instance has none)
     * @throws IiqApiException if a request fails or a response cannot be parsed
     */
    public List<Account> getAllAccounts() {
        List<Account> accounts = new ArrayList<>();

        int startIndex = 1;          // SCIM is 1-based
        int totalResults = Integer.MAX_VALUE; // unknown until the first response
        int pageCount = 0;

        while (accounts.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException(
                        "Aborting after " + MAX_PAGES + " pages; the server may not be honouring pagination.");
            }

            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            String body = client.get(ACCOUNTS_PATH, query);
            JsonNode root = parse(body);

            // Update the authoritative total from every page (avoids hardcoding a count).
            totalResults = root.path("totalResults").asInt(0);

            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                // No (more) results — stop even if totalResults claimed otherwise.
                break;
            }

            for (JsonNode accountNode : resources) {
                accounts.add(toAccount(accountNode));
            }

            // Advance by the number of resources actually returned on this page.
            startIndex += resources.size();
        }

        return accounts;
    }

    // --- parsing ------------------------------------------------------------

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + ACCOUNTS_PATH, e);
        }
    }

    /** Maps a single SCIM Account resource node into an {@link Account}. */
    private Account toAccount(JsonNode node) {
        String id = text(node, "id");
        String displayName = text(node, "displayName");
        String nativeIdentity = text(node, "nativeIdentity");
        Boolean active = bool(node, "active");
        Boolean manuallyCorrelated = bool(node, "manuallyCorrelated");
        Boolean locked = bool(node, "locked");
        Boolean hasEntitlements = bool(node, "hasEntitlements");
        String lastRefresh = text(node, "lastRefresh");

        Account.Ref application = parseRef(node.path("application"));
        Account.Ref identity = parseRef(node.path("identity"));
        Account.Meta meta = parseMeta(node.path("meta"));
        List<String> schemas = parseSchemas(node.path("schemas"));

        ObjectNode additional = extractAdditionalAttributes(node);

        return new Account(id, displayName, nativeIdentity, active, manuallyCorrelated,
                locked, hasEntitlements, lastRefresh, application, identity, meta, schemas, additional);
    }

    /**
     * Deep-copies the resource and removes the core fields, leaving a node that
     * holds every application-specific / unknown attribute with its original JSON
     * structure fully intact (scalars, arrays, nested objects, arrays of objects).
     */
    private ObjectNode extractAdditionalAttributes(JsonNode node) {
        if (!node.isObject()) {
            return mapper.createObjectNode();
        }
        ObjectNode copy = node.deepCopy();
        copy.remove(CORE_FIELDS);
        return copy;
    }

    private static Account.Ref parseRef(JsonNode ref) {
        if (ref == null || ref.isMissingNode() || ref.isNull()) {
            return null;
        }
        return new Account.Ref(
                text(ref, "displayName"),
                text(ref, "userName"), // only present on the identity reference
                text(ref, "value"),
                text(ref, "$ref"));
    }

    private static Account.Meta parseMeta(JsonNode meta) {
        if (meta == null || meta.isMissingNode() || meta.isNull()) {
            return null;
        }
        return new Account.Meta(
                text(meta, "resourceType"),
                text(meta, "location"),
                text(meta, "created"),
                text(meta, "lastModified"),
                text(meta, "version"));
    }

    private static List<String> parseSchemas(JsonNode schemas) {
        List<String> result = new ArrayList<>();
        if (schemas != null && schemas.isArray()) {
            for (JsonNode schema : schemas) {
                if (!schema.isNull()) {
                    result.add(schema.asText());
                }
            }
        }
        return result;
    }

    private static Boolean bool(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asBoolean() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
