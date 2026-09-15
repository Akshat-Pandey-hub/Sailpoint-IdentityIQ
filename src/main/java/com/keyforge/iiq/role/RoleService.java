package com.keyforge.iiq.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves Role records from IdentityIQ's SCIM v2 API.
 *
 * <p>The only endpoint used is {@code GET /scim/v2/Roles} (verified advertised in this
 * instance's {@code /scim/v2/ResourceTypes}). It returns a SCIM {@code ListResponse}.
 * Pagination mirrors {@link com.keyforge.iiq.entitlement.EntitlementService} exactly:
 * it walks the full result set using {@code startIndex} / {@code count} /
 * {@code totalResults}. Only fields actually present on the live Role resource are read;
 * the SCIM Role resource does not expose role→entitlement, so none is invented here.
 */
public class RoleService {

    /** SCIM Roles collection endpoint, relative to the configured base URL. */
    static final String ROLES_PATH = "scim/v2/Roles";

    static final int PAGE_SIZE = 100;

    private static final int MAX_PAGES = 10_000;

    private final IiqApiClient client;
    private final ObjectMapper mapper;

    public RoleService(IiqApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /**
     * Retrieves every Role from IdentityIQ, following pagination.
     *
     * @return all Roles (never null; empty if the instance has none)
     * @throws IiqApiException if a request fails or a response cannot be parsed
     */
    public List<Role> getAllRoles() {
        List<Role> roles = new ArrayList<>();

        int startIndex = 1;          // SCIM is 1-based
        int totalResults = Integer.MAX_VALUE;
        int pageCount = 0;

        while (roles.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException(
                        "Aborting after " + MAX_PAGES + " pages; the server may not be honouring pagination.");
            }

            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            String body = client.get(ROLES_PATH, query);
            JsonNode root = parse(body);

            totalResults = root.path("totalResults").asInt(0);

            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                break;
            }

            for (JsonNode roleNode : resources) {
                roles.add(toRole(roleNode));
            }

            startIndex += resources.size();
        }

        return roles;
    }

    // --- parsing ------------------------------------------------------------

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + ROLES_PATH, e);
        }
    }

    /** Maps a single SCIM Role resource node into a {@link Role}. */
    private Role toRole(JsonNode node) {
        String id = text(node, "id");
        String name = text(node, "name");
        String displayableName = text(node, "displayableName");
        Role.Type type = parseType(node.path("type"));
        Boolean active = node.hasNonNull("active") ? node.get("active").asBoolean() : null;
        Role.Ref owner = parseRef(node.path("owner"));
        JsonNode descriptions = arrayOrNull(node.path("descriptions"));
        String activationDate = text(node, "activationDate");
        String deactivationDate = text(node, "deactivationDate");
        JsonNode classifications = arrayOrNull(node.path("classifications"));
        Role.Meta meta = parseMeta(node.path("meta"));
        List<Role.Ref> inheritance = parseRefs(node.path("inheritance"));
        List<Role.Ref> requirements = parseRefs(node.path("requirements"));
        List<Role.Ref> permits = parseRefs(node.path("permits"));

        return new Role(id, name, displayableName, type, active, owner, descriptions,
                activationDate, deactivationDate, classifications, meta,
                inheritance, requirements, permits);
    }

    private static Role.Type parseType(JsonNode type) {
        if (type == null || type.isMissingNode() || type.isNull() || !type.isObject()) {
            return null;
        }
        return new Role.Type(text(type, "name"), text(type, "displayName"));
    }

    private static Role.Ref parseRef(JsonNode ref) {
        if (ref == null || ref.isMissingNode() || ref.isNull() || !ref.isObject()) {
            return null;
        }
        return new Role.Ref(text(ref, "value"), text(ref, "$ref"), text(ref, "displayName"));
    }

    private static List<Role.Ref> parseRefs(JsonNode array) {
        List<Role.Ref> refs = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode element : array) {
                Role.Ref ref = parseRef(element);
                if (ref != null) {
                    refs.add(ref);
                }
            }
        }
        return refs;
    }

    private static Role.Meta parseMeta(JsonNode meta) {
        if (meta == null || meta.isMissingNode() || meta.isNull()) {
            return null;
        }
        return new Role.Meta(
                text(meta, "resourceType"),
                text(meta, "location"),
                text(meta, "created"),
                text(meta, "lastModified"),
                text(meta, "version"));
    }

    private static JsonNode arrayOrNull(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
