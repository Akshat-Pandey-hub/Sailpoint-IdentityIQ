package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** Shared page-envelope reader for native endpoints that return raw JSON rows. */
final class KfAgentJsonRows {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KfAgentJsonRows() { }

    static List<JsonNode> parse(String json, String endpoint) {
        if (json == null || json.isBlank()) {
            throw new NativeImportException("Empty " + endpoint + " response payload");
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                throw new NativeImportException("Unexpected " + endpoint + " response (not a JSON object)");
            }
            JsonNode error = root.get("error");
            if (error != null && !error.isNull()) {
                throw new NativeImportException(endpoint + " endpoint returned an error: "
                        + error.path("type").asText("unknown") + ": " + error.path("message").asText(""));
            }
            JsonNode rows = root.path("rows");
            if (!rows.isArray()) {
                return List.of();
            }
            List<JsonNode> result = new ArrayList<>(rows.size());
            rows.forEach(result::add);
            return result;
        } catch (NativeImportException e) {
            throw e;
        } catch (Exception e) {
            throw new NativeImportException("Unparseable " + endpoint + " response: " + e.getMessage(), e);
        }
    }
}
