package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeViolationRecord}s. Fails loudly on a
 * non-JSON body, a controlled {@code error} object, or a body without a {@code rows} array. A genuine
 * {@code {"rows":[]}} returns empty.
 */
final class NativeViolationParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeViolationRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native PolicyViolation endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native PolicyViolation response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeViolationFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native PolicyViolation response (no '"
                    + NativeViolationFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeViolationRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeViolationRecord toRecord(JsonNode r) {
        NativeViolationRecord rec = new NativeViolationRecord();
        rec.sourceId = text(r, NativeViolationFields.SOURCE_ID);
        rec.name = text(r, NativeViolationFields.NAME);
        rec.identityId = text(r, NativeViolationFields.IDENTITY_ID);
        rec.identityName = text(r, NativeViolationFields.IDENTITY_NAME);
        rec.policyId = text(r, NativeViolationFields.POLICY_ID);
        rec.policyName = text(r, NativeViolationFields.POLICY_NAME);
        rec.constraintId = text(r, NativeViolationFields.CONSTRAINT_ID);
        rec.constraintName = text(r, NativeViolationFields.CONSTRAINT_NAME);
        rec.status = text(r, NativeViolationFields.STATUS);
        rec.active = bool(r, NativeViolationFields.ACTIVE);
        rec.leftBundles = text(r, NativeViolationFields.LEFT_BUNDLES);
        rec.rightBundles = text(r, NativeViolationFields.RIGHT_BUNDLES);
        rec.entitlementsMarkedForRemediation = text(r, NativeViolationFields.ENT_MARKED);
        rec.bundlesMarkedForRemediation = text(r, NativeViolationFields.BUNDLES_MARKED);
        rec.relevantAppsJson = json(r, NativeViolationFields.RELEVANT_APPS);
        rec.violatingEntitlementsJson = json(r, NativeViolationFields.VIOLATING_ENTITLEMENTS);
        rec.argumentsJson = json(r, NativeViolationFields.ARGUMENTS);
        rec.created = instant(r, NativeViolationFields.CREATED);
        rec.modified = instant(r, NativeViolationFields.MODIFIED);
        rec.srcSystem = text(r, NativeViolationFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeViolationFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeViolationFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeViolationFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeViolationFields.EXTRACTED_AT);
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
