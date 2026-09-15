package com.keyforge.iiq.entitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.Entitlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves Entitlement records from IdentityIQ's SCIM v2 API.
 *
 * <p>The only endpoint used is {@code GET /scim/v2/Entitlements}, which returns a
 * SCIM {@code ListResponse}. Pagination mirrors
 * {@link com.keyforge.iiq.identity.IdentityService} and
 * {@link com.keyforge.iiq.application.ApplicationService}: it walks the full
 * result set using {@code startIndex} / {@code count} / {@code totalResults} so
 * every Entitlement is retrieved regardless of page size.
 */
public class EntitlementService {

    /** SCIM Entitlements collection endpoint, relative to the configured base URL. */
    static final String ENTITLEMENTS_PATH = "scim/v2/Entitlements";

    /**
     * Page size requested from the server. The server may return fewer per page
     * (or return everything at once); the loop advances on the number of
     * resources actually returned so both cases are handled.
     */
    static final int PAGE_SIZE = 100;

    /** Backstop against a misbehaving server to avoid an unbounded loop. */
    private static final int MAX_PAGES = 10_000;

    private final IiqApiClient client;
    private final ObjectMapper mapper;

    public EntitlementService(IiqApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /**
     * Retrieves every Entitlement from IdentityIQ, following pagination.
     *
     * @return all Entitlements (never null; empty if the instance has none)
     * @throws IiqApiException if a request fails or a response cannot be parsed
     */
    public List<Entitlement> getAllEntitlements() {
        List<Entitlement> entitlements = new ArrayList<>();

        int startIndex = 1;          // SCIM is 1-based
        int totalResults = Integer.MAX_VALUE; // unknown until the first response
        int pageCount = 0;

        while (entitlements.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException(
                        "Aborting after " + MAX_PAGES + " pages; the server may not be honouring pagination.");
            }

            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            String body = client.get(ENTITLEMENTS_PATH, query);
            JsonNode root = parse(body);

            // Update the authoritative total from every page (avoids hardcoding a count).
            totalResults = root.path("totalResults").asInt(0);

            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                // No (more) results — stop even if totalResults claimed otherwise.
                break;
            }

            for (JsonNode entNode : resources) {
                entitlements.add(toEntitlement(entNode));
            }

            // Advance by the number of resources actually returned on this page.
            startIndex += resources.size();
        }

        return entitlements;
    }

    // --- parsing ------------------------------------------------------------

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + ENTITLEMENTS_PATH, e);
        }
    }

    /** Maps a single SCIM Entitlement resource node into an {@link Entitlement}. */
    private Entitlement toEntitlement(JsonNode node) {
        String id = text(node, "id");
        String displayableName = text(node, "displayableName");
        String value = text(node, "value");
        String attribute = text(node, "attribute");
        Boolean requestable = node.hasNonNull("requestable") ? node.get("requestable").asBoolean() : null;
        String type = text(node, "type");
        Entitlement.ApplicationRef application = parseApplicationRef(node.path("application"));
        Entitlement.Meta meta = parseMeta(node.path("meta"));
        com.fasterxml.jackson.databind.node.ObjectNode additional = extractAdditionalAttributes(node);

        return new Entitlement(id, displayableName, value, attribute, requestable, type, application, meta, additional);
    }

    /**
     * Preserves the full raw entitlement resource except the fields fully captured in
     * dedicated columns ({@code id}, {@code value}, {@code displayableName}). Keeps
     * the original JSON structure so nothing is lost — including {@code type},
     * {@code attribute}, {@code requestable}, {@code application}, {@code meta}, and
     * any field we do not explicitly model.
     */
    private com.fasterxml.jackson.databind.node.ObjectNode extractAdditionalAttributes(JsonNode node) {
        if (!node.isObject()) {
            return mapper.createObjectNode();
        }
        com.fasterxml.jackson.databind.node.ObjectNode copy = node.deepCopy();
        copy.remove(java.util.Set.of("id", "value", "displayableName"));
        return copy;
    }

    private static Entitlement.ApplicationRef parseApplicationRef(JsonNode application) {
        if (application == null || application.isMissingNode() || application.isNull()) {
            return null;
        }
        return new Entitlement.ApplicationRef(
                text(application, "displayName"),
                text(application, "value"),
                text(application, "$ref"));
    }

    /** Additive: preserves the SCIM {@code meta} block already present in the response. */
    private static Entitlement.Meta parseMeta(JsonNode meta) {
        if (meta == null || meta.isMissingNode() || meta.isNull()) {
            return null;
        }
        return new Entitlement.Meta(
                text(meta, "resourceType"),
                text(meta, "location"),
                text(meta, "created"),
                text(meta, "lastModified"),
                text(meta, "version"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
