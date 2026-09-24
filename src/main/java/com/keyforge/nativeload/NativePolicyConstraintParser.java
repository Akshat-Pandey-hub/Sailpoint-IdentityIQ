package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativePolicyConstraintRecord}s. Fails loudly on
 * non-JSON, a controlled {@code error} object, or a missing {@code rows} array. Exposes {@code sourceCount}
 * (total policies) and {@code returnedPolicies} (policies in this page) for the page-by-parent scan guard.
 */
final class NativePolicyConstraintParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        return intField(json, NativePolicyConstraintFields.SOURCE_COUNT);
    }

    int returnedPolicies(String json) {
        return intField(json, NativePolicyConstraintFields.RETURNED_POLICIES);
    }

    private int intField(String json, String field) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(field);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativePolicyConstraintRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native PolicyConstraint endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native PolicyConstraint response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativePolicyConstraintFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native PolicyConstraint response (no '"
                    + NativePolicyConstraintFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativePolicyConstraintRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativePolicyConstraintRecord toRecord(JsonNode r) {
        NativePolicyConstraintRecord rec = new NativePolicyConstraintRecord();
        rec.sourceId = text(r, NativePolicyConstraintFields.SOURCE_ID);
        rec.policyId = text(r, NativePolicyConstraintFields.POLICY_ID);
        rec.policyName = text(r, NativePolicyConstraintFields.POLICY_NAME);
        rec.name = text(r, NativePolicyConstraintFields.NAME);
        rec.description = text(r, NativePolicyConstraintFields.DESCRIPTION);
        rec.constraintType = text(r, NativePolicyConstraintFields.CONSTRAINT_TYPE);
        rec.weight = intOrNull(r, NativePolicyConstraintFields.WEIGHT);
        rec.compensatingControl = text(r, NativePolicyConstraintFields.COMPENSATING_CONTROL);
        rec.violationOwnerId = text(r, NativePolicyConstraintFields.VIOLATION_OWNER_ID);
        rec.violationOwnerName = text(r, NativePolicyConstraintFields.VIOLATION_OWNER_NAME);
        rec.violationOwnerType = text(r, NativePolicyConstraintFields.VIOLATION_OWNER_TYPE);
        rec.leftBundlesJson = json(r, NativePolicyConstraintFields.LEFT_BUNDLES);
        rec.rightBundlesJson = json(r, NativePolicyConstraintFields.RIGHT_BUNDLES);
        rec.selectorsJson = json(r, NativePolicyConstraintFields.SELECTORS);
        rec.selectorCount = intOrNull(r, NativePolicyConstraintFields.SELECTOR_COUNT);
        rec.argumentsJson = json(r, NativePolicyConstraintFields.ARGUMENTS);
        rec.created = instant(r, NativePolicyConstraintFields.CREATED);
        rec.modified = instant(r, NativePolicyConstraintFields.MODIFIED);
        rec.srcSystem = text(r, NativePolicyConstraintFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativePolicyConstraintFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativePolicyConstraintFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativePolicyConstraintFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativePolicyConstraintFields.EXTRACTED_AT);
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
