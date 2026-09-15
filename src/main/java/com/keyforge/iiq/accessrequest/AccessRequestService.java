package com.keyforge.iiq.accessrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiException;
import com.keyforge.iiq.client.IiqSessionClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves Access Requests (IdentityRequest) from the IdentityIQ modern-UI endpoint
 * {@code GET ui/rest/identityRequests} (verified live). This classic {@code ui/rest} GET
 * requires the session's CSRF token (redirects to login without it), so the shared
 * {@link IiqSessionClient} session is warmed via {@code warmCsrfToken()} before reading, and
 * the token is echoed by the client's {@code get}. Envelope: {@code {"objects":[...],"count":N}}.
 * Each object carries request-level fields, {@code items[]}, and {@code interactions[]}.
 */
public class AccessRequestService {

    static final String REQUESTS_PATH = "ui/rest/identityRequests";
    static final String OBJECTS_KEY = "objects";
    static final String TOTAL_KEY = "count";
    static final int PAGE_LIMIT = 50;
    private static final int MAX_PAGES = 10_000;

    private final IiqSessionClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AccessRequestService(IiqSessionClient client) {
        this.client = client;
    }

    public List<AccessRequest> getAllRequests() {
        client.warmCsrfToken(); // this GET endpoint requires the CSRF token
        List<AccessRequest> requests = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        int pageCount = 0;

        while (requests.size() < total) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + REQUESTS_PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("start", Integer.toString(start));
            query.put("limit", Integer.toString(PAGE_LIMIT));

            String body = client.get(REQUESTS_PATH, query);
            List<AccessRequest> page = parseRequests(body);
            int declaredTotal = parseTotal(body);
            if (page.isEmpty()) {
                break;
            }
            requests.addAll(page);
            if (declaredTotal < 0) {
                break;
            }
            total = declaredTotal;
            start += page.size();
        }
        return requests;
    }

    // --- pure parsing (unit-testable) ---------------------------------------

    public List<AccessRequest> parseRequests(String json) {
        List<AccessRequest> out = new ArrayList<>();
        JsonNode root = parse(json);
        JsonNode array = root.get(OBJECTS_KEY);
        if (array != null && array.isArray()) {
            for (JsonNode r : array) {
                out.add(toRequest(r));
            }
        }
        return out;
    }

    int parseTotal(String json) {
        JsonNode v = parse(json).get(TOTAL_KEY);
        return v != null && v.isNumber() ? v.asInt() : -1;
    }

    private AccessRequest toRequest(JsonNode r) {
        List<AccessRequest.Item> items = new ArrayList<>();
        JsonNode itemsNode = r.get("items");
        if (itemsNode != null && itemsNode.isArray()) {
            for (JsonNode it : itemsNode) {
                items.add(new AccessRequest.Item(
                        text(it, "id"), text(it, "operation"), text(it, "applicationName"),
                        text(it, "accountName"), text(it, "displayableAccountName"), text(it, "instance"),
                        text(it, "name"), text(it, "value"), text(it, "displayableValue"),
                        bool(it, "role"), bool(it, "entitlement"), bool(it, "hasManagedAttribute"),
                        text(it, "approvalState"), text(it, "provisioningState"), text(it, "provisioningEngine"),
                        text(it, "provisioningRequestId"), text(it, "assignmentId"), integer(it, "retries"),
                        text(it, "requesterComments"), epoch(it, "startDate"), epoch(it, "endDate")));
            }
        }
        List<AccessRequest.Approval> approvals = new ArrayList<>();
        JsonNode inter = r.get("interactions");
        if (inter != null && inter.isArray()) {
            for (JsonNode a : inter) {
                approvals.add(new AccessRequest.Approval(
                        text(a, "workItemId"), text(a, "workItemName"), text(a, "workItemArchiveId"),
                        text(a, "ownerDisplayName"), text(a, "comments"), text(a, "status"),
                        text(a, "description"), integer(a, "approvalItemCount"),
                        epoch(a, "openDate"), epoch(a, "completeDate")));
            }
        }
        return new AccessRequest(
                text(r, "id"), text(r, "requestId"), text(r, "type"),
                text(r, "requesterDisplayName"), text(r, "targetDisplayName"), text(r, "state"),
                text(r, "executionStatus"), text(r, "completionStatus"), text(r, "priority"),
                text(r, "externalTicketId"), bool(r, "cancelable"),
                epoch(r, "createdDate"), epoch(r, "endDate"), epoch(r, "terminatedDate"), epoch(r, "verificationDate"),
                items, approvals);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse Access Requests response from " + REQUESTS_PATH, e);
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

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.asBoolean() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asInt() : null;
    }

    private static Long epoch(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asLong() : null;
    }
}
