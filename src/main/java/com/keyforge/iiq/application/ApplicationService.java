package com.keyforge.iiq.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.Application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves Application records from IdentityIQ's SCIM v2 API.
 *
 * <p>The only endpoint used is {@code GET /scim/v2/Applications}, which returns a
 * SCIM {@code ListResponse}. Pagination mirrors
 * {@link com.keyforge.iiq.identity.IdentityService}: it walks the full result set
 * using {@code startIndex} / {@code count} / {@code totalResults} so every
 * Application is retrieved regardless of page size.
 */
public class ApplicationService {

    /** SCIM Applications collection endpoint, relative to the configured base URL. */
    static final String APPLICATIONS_PATH = "scim/v2/Applications";

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

    public ApplicationService(IiqApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /**
     * Retrieves every Application from IdentityIQ, following pagination.
     *
     * @return all Applications (never null; empty if the instance has none)
     * @throws IiqApiException if a request fails or a response cannot be parsed
     */
    public List<Application> getAllApplications() {
        List<Application> applications = new ArrayList<>();

        int startIndex = 1;          // SCIM is 1-based
        int totalResults = Integer.MAX_VALUE; // unknown until the first response
        int pageCount = 0;

        while (applications.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException(
                        "Aborting after " + MAX_PAGES + " pages; the server may not be honouring pagination.");
            }

            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            String body = client.get(APPLICATIONS_PATH, query);
            JsonNode root = parse(body);

            // Update the authoritative total from every page (avoids hardcoding a count).
            totalResults = root.path("totalResults").asInt(0);

            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                // No (more) results — stop even if totalResults claimed otherwise.
                break;
            }

            for (JsonNode appNode : resources) {
                applications.add(toApplication(appNode));
            }

            // Advance by the number of resources actually returned on this page.
            startIndex += resources.size();
        }

        return applications;
    }

    // --- parsing ------------------------------------------------------------

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + APPLICATIONS_PATH, e);
        }
    }

    /** Maps a single SCIM Application resource node into an {@link Application}. */
    private Application toApplication(JsonNode node) {
        String id = text(node, "id");
        String name = text(node, "name");
        String type = text(node, "type");
        Application.Owner owner = parseOwner(node.path("owner"));
        List<Application.ApplicationSchema> schemas = parseSchemas(node.path("applicationSchemas"));
        Application.Meta meta = parseMeta(node.path("meta"));
        com.fasterxml.jackson.databind.node.ObjectNode additional = extractAdditionalAttributes(node);

        return new Application(id, name, type, owner, schemas, meta, additional);
    }

    /**
     * Preserves the full raw Application resource except the fields captured by the
     * typed fields above, so nothing (e.g. {@code descriptions}, {@code schemas}, any
     * unmodelled field) is lost.
     */
    private com.fasterxml.jackson.databind.node.ObjectNode extractAdditionalAttributes(JsonNode node) {
        if (!node.isObject()) {
            return mapper.createObjectNode();
        }
        com.fasterxml.jackson.databind.node.ObjectNode copy = node.deepCopy();
        copy.remove(java.util.Set.of("id", "name", "type", "owner", "applicationSchemas", "meta"));
        return copy;
    }

    private static Application.Owner parseOwner(JsonNode owner) {
        if (owner == null || owner.isMissingNode() || owner.isNull()) {
            return null;
        }
        String value = text(owner, "value");
        String ref = text(owner, "$ref");
        // Prefer displayName; fall back to the SCIM-standard "display".
        String displayName = text(owner, "displayName");
        if (displayName == null) {
            displayName = text(owner, "display");
        }
        return new Application.Owner(value, ref, displayName);
    }

    private static List<Application.ApplicationSchema> parseSchemas(JsonNode schemas) {
        List<Application.ApplicationSchema> result = new ArrayList<>();
        if (schemas != null && schemas.isArray()) {
            for (JsonNode schema : schemas) {
                result.add(new Application.ApplicationSchema(
                        text(schema, "type"),
                        text(schema, "value"),
                        text(schema, "$ref")));
            }
        }
        return result;
    }

    private static Application.Meta parseMeta(JsonNode meta) {
        if (meta == null || meta.isMissingNode() || meta.isNull()) {
            return null;
        }
        return new Application.Meta(
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
