package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeGroupDefinitionRecord}s. Fails loudly on
 * a non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array. A genuine {@code {"rows":[]}} returns empty. ISO-8601 timestamps become {@link Instant} (a bad
 * timestamp becomes {@code null}).
 */
final class NativeGroupDefinitionParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeGroupDefinitionRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native GroupDefinition endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native GroupDefinition response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            String type = error.path("type").asText("unknown");
            String message = error.path("message").asText("");
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + type
                    + (message.isEmpty() ? "" : ": " + message));
        }
        JsonNode rows = root.get(NativeGroupDefinitionFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native GroupDefinition response (no '"
                    + NativeGroupDefinitionFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeGroupDefinitionRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeGroupDefinitionRecord toRecord(JsonNode r) {
        NativeGroupDefinitionRecord rec = new NativeGroupDefinitionRecord();
        rec.sourceId = text(r, NativeGroupDefinitionFields.SOURCE_ID);
        rec.name = text(r, NativeGroupDefinitionFields.NAME);
        rec.type = text(r, NativeGroupDefinitionFields.TYPE);
        rec.factoryId = text(r, NativeGroupDefinitionFields.FACTORY_ID);
        rec.factoryName = text(r, NativeGroupDefinitionFields.FACTORY_NAME);
        rec.filterExpression = text(r, NativeGroupDefinitionFields.FILTER_EXPRESSION);
        rec.isPrivate = bool(r, NativeGroupDefinitionFields.IS_PRIVATE);
        rec.indexed = bool(r, NativeGroupDefinitionFields.INDEXED);
        rec.nullGroup = bool(r, NativeGroupDefinitionFields.NULL_GROUP);
        rec.nameUnique = bool(r, NativeGroupDefinitionFields.NAME_UNIQUE);
        rec.ownerId = text(r, NativeGroupDefinitionFields.OWNER_ID);
        rec.ownerName = text(r, NativeGroupDefinitionFields.OWNER_NAME);
        rec.lastRefresh = instant(r, NativeGroupDefinitionFields.LAST_REFRESH);
        rec.created = instant(r, NativeGroupDefinitionFields.CREATED);
        rec.modified = instant(r, NativeGroupDefinitionFields.MODIFIED);

        rec.srcSystem = text(r, NativeGroupDefinitionFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeGroupDefinitionFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeGroupDefinitionFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeGroupDefinitionFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeGroupDefinitionFields.EXTRACTED_AT);
        return rec;
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static Boolean bool(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : Boolean.valueOf(n.asBoolean());
    }

    private static Instant instant(JsonNode r, String field) {
        String s = text(r, field);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String snippet(String json) {
        if (json == null || json.isEmpty()) {
            return "<empty>";
        }
        String s = json.strip().replaceAll("\\s+", " ");
        return s.length() <= 500 ? s : s.substring(0, 500) + "... (truncated)";
    }
}
