package com.keyforge.iiq.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.client.IiqApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves Workflow definitions from IdentityIQ's SCIM v2 API ({@code GET /scim/v2/Workflows},
 * verified advertised). Standard SCIM ListResponse pagination, mirroring
 * {@link com.keyforge.iiq.role.RoleService}. Only the fields the SCIM schema exposes are read;
 * approval configuration is not exposed by SCIM and is never reconstructed here.
 */
public class WorkflowService {

    static final String WORKFLOWS_PATH = "scim/v2/Workflows";
    static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10_000;

    private final IiqApiClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkflowService(IiqApiClient client) {
        this.client = client;
    }

    public List<WorkflowDefinition> getAllWorkflows() {
        List<WorkflowDefinition> workflows = new ArrayList<>();
        int startIndex = 1;
        int totalResults = Integer.MAX_VALUE;
        int pageCount = 0;

        while (workflows.size() < totalResults) {
            if (++pageCount > MAX_PAGES) {
                throw new IiqApiException("Aborting after " + MAX_PAGES + " pages for " + WORKFLOWS_PATH);
            }
            Map<String, String> query = new LinkedHashMap<>();
            query.put("startIndex", Integer.toString(startIndex));
            query.put("count", Integer.toString(PAGE_SIZE));

            JsonNode root = parse(client.get(WORKFLOWS_PATH, query));
            totalResults = root.path("totalResults").asInt(0);
            JsonNode resources = root.path("Resources");
            if (!resources.isArray() || resources.isEmpty()) {
                break;
            }
            for (JsonNode node : resources) {
                workflows.add(toWorkflow(node));
            }
            startIndex += resources.size();
        }
        return workflows;
    }

    private WorkflowDefinition toWorkflow(JsonNode node) {
        JsonNode meta = node.path("meta");
        return new WorkflowDefinition(
                text(node, "id"),
                text(node, "name"),
                text(node, "type"),
                text(node, "handler"),
                text(node, "description"),
                text(meta, "created"),
                text(meta, "lastModified"));
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IiqApiException("Failed to parse SCIM response from " + WORKFLOWS_PATH, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
