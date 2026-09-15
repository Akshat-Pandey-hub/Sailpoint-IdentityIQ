package com.keyforge.iiq.usergroup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.UserGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves User Group objects from IdentityIQ using the three data-source endpoints
 * that the IdentityIQ "Group Configuration" screen ({@code /define/groups/groups.jsf})
 * itself calls. Uses the shared {@link IiqApiClient} (same Basic-auth HTTP client as
 * Services #1–#7).
 *
 * <p>Endpoints (each maps to one category — the endpoint determines the category,
 * never the record content):
 * <ul>
 *     <li>{@code define/groups/workgroupsDataSource.json}  → Workgroup</li>
 *     <li>{@code define/groups/populationsDataSource.json} → Population</li>
 *     <li>{@code define/groups/groupsDataSource.json}      → Group</li>
 * </ul>
 *
 * <p>These are ExtJS grid data sources, not SCIM. The response is an ExtJS store
 * page — a total count plus a rows array. On this IdentityIQ 8.4 instance each endpoint
 * returns its own array key ({@code workgroups} / {@code populations} / {@code groups})
 * plus {@code totalCount}; the parser also tolerates other common ExtJS keys. Pagination
 * uses ExtJS {@code start}/{@code limit}/{@code page} plus the per-request cache-buster
 * {@code _dc}, exactly as the browser sends. Every record's COMPLETE raw object is
 * preserved so no field is lost, and each
 * endpoint is attempted independently: a zero-record source reports zero, an erroring
 * source is reported as failed (never silently treated as empty), and the others still
 * extract.
 */
public class UserGroupService {

    static final String WORKGROUPS_PATH = "define/groups/workgroupsDataSource.json";
    static final String POPULATIONS_PATH = "define/groups/populationsDataSource.json";
    static final String GROUPS_PATH = "define/groups/groupsDataSource.json";

    public static final String TYPE_WORKGROUP = "Workgroup";
    public static final String TYPE_POPULATION = "Population";
    public static final String TYPE_GROUP = "Group";

    /** ExtJS page size — matches the Group Configuration UI's request (start/limit/page). */
    static final int PAGE_LIMIT = 25;
    private static final int MAX_PAGES = 10_000;

    private static final String[] TOTAL_KEYS = {"totalCount", "total", "count", "totalResults"};
    private static final String[] ARRAY_KEYS = {"objects", "rows", "data", "results",
            "workgroups", "populations", "groups"};

    private final IiqApiClient client;
    private final ObjectMapper mapper;

    public UserGroupService(IiqApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /** Result of an extraction run: all objects plus a per-source status line. */
    public static final class Result {
        private final List<UserGroup> userGroups;
        private final List<String> sourceStatus;
        private final boolean anyEndpointFailed;

        Result(List<UserGroup> userGroups, List<String> sourceStatus, boolean anyEndpointFailed) {
            this.userGroups = userGroups;
            this.sourceStatus = sourceStatus;
            this.anyEndpointFailed = anyEndpointFailed;
        }

        public List<UserGroup> getUserGroups() {
            return userGroups;
        }

        /** One line per endpoint: record count, or the failure reason. */
        public List<String> getSourceStatus() {
            return sourceStatus;
        }

        public boolean anyEndpointFailed() {
            return anyEndpointFailed;
        }
    }

    /** Extracts Workgroups, Populations and Groups, each from its own endpoint. */
    public Result extractAll() {
        List<UserGroup> all = new ArrayList<>();
        List<String> status = new ArrayList<>();
        boolean anyFailed = false;

        String[][] sources = {
                {WORKGROUPS_PATH, TYPE_WORKGROUP},
                {POPULATIONS_PATH, TYPE_POPULATION},
                {GROUPS_PATH, TYPE_GROUP},
        };

        for (String[] source : sources) {
            String path = source[0];
            String type = source[1];
            try {
                List<UserGroup> got = new ArrayList<>();
                fetchGrid(path, type, got);
                all.addAll(got);
                status.add(type + " (" + path + "): " + got.size() + " record(s)");
            } catch (IiqApiException e) {
                anyFailed = true;
                String reason = e.hasStatusCode() ? "HTTP " + e.getStatusCode() : e.getMessage();
                status.add(type + " (" + path + "): FAILED — " + reason);
            }
        }

        return new Result(all, status, anyFailed);
    }

    // --- ExtJS grid extraction ----------------------------------------------

    /** Walks an ExtJS grid data source with start/limit/page paging until all rows are read. */
    private void fetchGrid(String path, String type, List<UserGroup> out) {
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (out.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + path);
            }

            Map<String, String> query = new LinkedHashMap<>();
            // Cache-buster generated per request (not hardcoded), like the browser's _dc.
            query.put("_dc", Long.toString(System.currentTimeMillis()));
            query.put("start", Integer.toString(start));
            query.put("limit", Integer.toString(PAGE_LIMIT));
            query.put("page", Integer.toString(start / PAGE_LIMIT + 1));

            JsonNode root = parse(client.get(path, query), path);

            JsonNode rows = firstArray(root, ARRAY_KEYS);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (JsonNode node : rows) {
                out.add(toUserGroup(node, type));
            }

            int declaredTotal = firstInt(root, TOTAL_KEYS);
            if (declaredTotal < 0) {
                break; // no total provided -> single page, nothing more to fetch
            }
            total = declaredTotal;
            start += rows.size();
        }
    }

    private JsonNode parse(String body, String path) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse User Group response from " + path, e);
        }
    }

    /**
     * Maps one grid record to a {@link UserGroup}. The category comes from the endpoint
     * ({@code type}), never inferred from the content. Common fields are read when
     * present; the COMPLETE raw record is preserved verbatim.
     */
    private UserGroup toUserGroup(JsonNode node, String type) {
        String id = firstText(node, "id", "value");
        String name = firstText(node, "name", "displayName", "displayableName");
        String description = firstText(node, "description");
        String status = firstText(node, "status");

        UserGroup.Ref owner = parseRef(firstNode(node, "owner", "ownerName", "owner_name"));

        List<UserGroup.Ref> members = new ArrayList<>();
        JsonNode memberArray = firstNode(node, "members", "memberList");
        boolean membersProvided = memberArray != null && memberArray.isArray();
        if (membersProvided) {
            for (JsonNode m : memberArray) {
                UserGroup.Ref ref = parseRef(m);
                if (ref != null) {
                    members.add(ref);
                }
            }
        }

        JsonNode rule = firstNode(node, "filter", "rule", "criteria", "selector", "ipop");
        UserGroup.Meta meta = new UserGroup.Meta(null, null,
                firstText(node, "created", "createdDate"),
                firstText(node, "modified", "lastModified", "modifiedDate"),
                null);

        // Preserve the COMPLETE raw record — nothing removed.
        ObjectNode additional = node.isObject() ? ((ObjectNode) node).deepCopy() : mapper.createObjectNode();

        return new UserGroup(id, name, type, description, owner, members, membersProvided,
                rule, status, meta, additional);
    }

    /** A reference may be an object ({value,id,display,name}) or a bare name string. */
    private static UserGroup.Ref parseRef(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isValueNode()) {
            String text = node.asText();
            return text == null || text.isBlank() ? null : new UserGroup.Ref(null, null, text);
        }
        if (!node.isObject()) {
            return null;
        }
        String value = firstText(node, "value", "id");
        String ref = firstText(node, "$ref");
        String display = firstText(node, "display", "displayName", "name");
        if (value == null && ref == null && display == null) {
            return null;
        }
        return new UserGroup.Ref(value, ref, display);
    }

    // --- helpers ------------------------------------------------------------

    private static JsonNode firstArray(JsonNode root, String... keys) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        for (String key : keys) {
            JsonNode node = root.get(key);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private static int firstInt(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && v.isNumber()) {
                return v.asInt();
            }
            if (v != null && v.isTextual()) {
                try {
                    return Integer.parseInt(v.asText().trim());
                } catch (NumberFormatException ignored) {
                    // not a number; keep looking
                }
            }
        }
        return -1;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode v = node.path(field);
            if (!v.isMissingNode() && !v.isNull() && v.isValueNode()) {
                String s = v.asText();
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull() && !v.isMissingNode()) {
                return v;
            }
        }
        return null;
    }
}
