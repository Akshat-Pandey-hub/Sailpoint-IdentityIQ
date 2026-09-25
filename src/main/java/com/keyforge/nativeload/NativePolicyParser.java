package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativePolicyRecord}s. Fails loudly on a
 * non-JSON body, a controlled {@code error} object, or a body without a {@code rows} array.
 */
final class NativePolicyParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativePolicyRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native Policy endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native Policy response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativePolicyFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native Policy response (no '"
                    + NativePolicyFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativePolicyRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativePolicyRecord toRecord(JsonNode r) {
        NativePolicyRecord rec = new NativePolicyRecord();
        rec.sourceId = text(r, NativePolicyFields.SOURCE_ID);
        rec.name = text(r, NativePolicyFields.NAME);
        rec.type = text(r, NativePolicyFields.TYPE);
        rec.typeKey = text(r, NativePolicyFields.TYPE_KEY);
        rec.description = text(r, NativePolicyFields.DESCRIPTION);
        rec.descriptionsJson = json(r, NativePolicyFields.DESCRIPTIONS);
        rec.executor = text(r, NativePolicyFields.EXECUTOR);
        rec.violationOwnerId = text(r, NativePolicyFields.VIOLATION_OWNER_ID);
        rec.violationOwnerName = text(r, NativePolicyFields.VIOLATION_OWNER_NAME);
        rec.constraintCount = intOrNull(r, NativePolicyFields.CONSTRAINT_COUNT);
        rec.state = text(r, NativePolicyFields.STATE);
        rec.violationRule = text(r, NativePolicyFields.VIOLATION_RULE);
        rec.violationWorkflow = text(r, NativePolicyFields.VIOLATION_WORKFLOW);
        rec.signature = text(r, NativePolicyFields.SIGNATURE);
        rec.certificationActions = text(r, NativePolicyFields.CERTIFICATION_ACTIONS);
        rec.created = instant(r, NativePolicyFields.CREATED);
        rec.modified = instant(r, NativePolicyFields.MODIFIED);
        rec.srcSystem = text(r, NativePolicyFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativePolicyFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativePolicyFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativePolicyFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativePolicyFields.EXTRACTED_AT);
        return rec;
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static Integer intOrNull(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : Integer.valueOf(n.asInt());
    }

    private static String json(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : n.toString();
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
