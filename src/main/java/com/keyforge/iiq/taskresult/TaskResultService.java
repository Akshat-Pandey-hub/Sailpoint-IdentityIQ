package com.keyforge.iiq.taskresult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves TaskResults from IdentityIQ's SCIM v2 API ({@code GET /scim/v2/TaskResults}, verified
 * live). Standard SCIM ListResponse pagination, mirroring {@link com.keyforge.iiq.workflow.WorkflowService}.
 * Only the fields the SCIM schema exposes are read; nothing is reconstructed. Extraction-only.
 */
public class TaskResultService {

    static final String TASK_RESULTS_PATH = "scim/v2/TaskResults";
    static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqApiClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public TaskResultService(IiqApiClient client) {
        this.client = client;
    }

    public List<TaskResult> getAllTaskResults() {
        List<TaskResult> results = new ArrayList<>();
        int startIndex = 1;
        int totalResults = Integer.MAX_VALUE;
        int pageCount = 0;

        while (results.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + TASK_RESULTS_PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            JsonNode root = parse(client.get(TASK_RESULTS_PATH, query));
            totalResults = root.path("totalResults").asInt(0);
            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                break;
            }
            for (JsonNode node : resources) {
                results.add(toTaskResult(node));
            }
            startIndex += resources.size();
        }
        return results;
    }

    // --- pure parsing (unit-testable) ---------------------------------------

    public List<TaskResult> parseTaskResults(String json) {
        List<TaskResult> out = new ArrayList<>();
        JsonNode resources = parse(json).path("Resources");
        if (resources.isArray()) {
            for (JsonNode node : resources) {
                out.add(toTaskResult(node));
            }
        }
        return out;
    }

    private TaskResult toTaskResult(JsonNode node) {
        JsonNode messages = node.path("messages");
        return new TaskResult(
                text(node, "id"),
                text(node, "name"),
                text(node, "type"),
                text(node, "taskDefinition"),
                text(node, "completionStatus"),
                text(node, "host"),
                text(node, "launcher"),
                text(node, "launched"),
                text(node, "completed"),
                bool(node, "partitioned"),
                bool(node, "terminated"),
                integer(node, "pendingSignoffs"),
                messages.isArray() ? messages.toString() : null);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + TASK_RESULTS_PATH, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.asBoolean() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asInt() : null;
    }
}
