package com.keyforge.iiq.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.model.WorkItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves Work Items from IdentityIQ using the exact request the classic Work Items
 * page ({@code /identityiq/workitem/workItems.jsf}) fires: a JSON
 * {@code POST /identityiq/ui/rest/workItems/} with a paging body. Uses a session-
 * authenticated {@link IiqApiClient} (same JSF login as the User Group service).
 *
 * <p>The response is the IIQ REST envelope {@code {status, objects:[...], count,
 * complete}}. Every Work Item is read; nothing is invented or dropped. Paging uses the
 * body's {@code start}/{@code limit} and stops when all {@code count} rows are read
 * (or the server reports {@code complete}).
 */
public class WorkItemService {

    /** The exact endpoint captured from the live Work Items page. */
    static final String WORK_ITEMS_PATH = "ui/rest/workItems/";
    /** Page size sent in the request body. */
    static final int PAGE_LIMIT = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqApiClient client;
    private final ObjectMapper mapper;

    public WorkItemService(IiqApiClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /** Retrieves every Work Item, following the endpoint's start/limit paging. */
    public List<WorkItem> getAllWorkItems() {
        List<WorkItem> workItems = new ArrayList<>();

        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (workItems.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " Work Item pages.");
            }

            JsonNode root = parse(client.postJson(WORK_ITEMS_PATH, requestBody(start, PAGE_LIMIT)));

            JsonNode objects = root.path("objects");
            if (!objects.isArray() || objects.isEmpty()) {
                break;
            }
            for (JsonNode node : objects) {
                workItems.add(toWorkItem(node));
            }

            // Authoritative total from the envelope; if absent, this single page is all there is.
            JsonNode count = root.get("count");
            if (count != null && count.isNumber()) {
                total = count.asInt();
            } else {
                break;
            }
            if (root.path("complete").asBoolean(false)) {
                break;
            }
            start += objects.size();
        }

        return workItems;
    }

    /** The request body captured from the page (all owners, sorted by name DESC). */
    static String requestBody(int start, int limit) {
        return "{\"type\":[],\"start\":" + start + ",\"limit\":" + limit
                + ",\"sort\":\"[{\\\"property\\\":\\\"workItemName\\\",\\\"direction\\\":\\\"DESC\\\"}]\""
                + ",\"ownerFilterValue\":\"all_items_filter\"}";
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse Work Items response from " + WORK_ITEMS_PATH, e);
        }
    }

    /** Maps one Work Item node to a {@link WorkItem}, preserving values as the source sends them. */
    private WorkItem toWorkItem(JsonNode n) {
        return new WorkItem(
                text(n, "id"),
                text(n, "workItemName"),
                text(n, "workItemType"),
                text(n, "workItemState"),
                text(n, "accessRequestName"),
                text(n, "description"),
                text(n, "priority"),
                integer(n, "commentCount"),
                bool(n, "editable"),
                epoch(n, "created"),
                epoch(n, "notificationDate"),
                epoch(n, "expirationDate"),
                epoch(n, "wakeUpDate"),
                integer(n, "reminders"),
                integer(n, "escalationCount"),
                text(n, "completionComments"),
                text(n, "esigMeaning"),
                text(n, "certificationId"),
                bool(n, "disableForwarding"),
                bool(n, "forceClassicApprovalUI"),
                bool(n, "newTypeWorkItem"),
                ref(n.get("owner")),
                ref(n.get("requester")),
                ref(n.get("assignee")),
                ref(n.get("target")));
    }

    private static WorkItem.Ref ref(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        String id = text(node, "id");
        String name = text(node, "name");
        String display = text(node, "displayName");
        if (id == null && name == null && display == null) {
            return null;
        }
        return new WorkItem.Ref(id, name, display);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asInt() : null;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.asBoolean() : null;
    }

    private static Long epoch(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asLong() : null;
    }
}
