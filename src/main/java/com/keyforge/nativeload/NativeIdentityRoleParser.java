package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the native identity-role envelope (edges); exposes sourceCount + returnedIdentities for the guard. */
final class NativeIdentityRoleParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        return intField(json, NativeIdentityRoleFields.SOURCE_COUNT, -1);
    }

    int returnedIdentities(String json) {
        return intField(json, NativeIdentityRoleFields.RETURNED_IDENTITIES, -1);
    }

    private int intField(String json, String field, int def) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(field);
            return (n == null || n.isNull()) ? def : n.asInt(def);
        } catch (Exception e) {
            return def;
        }
    }

    List<NativeIdentityRoleRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native IdentityRole endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native IdentityRole response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + error.path("type").asText("unknown")
                    + ": " + error.path("message").asText(""));
        }
        JsonNode rows = root.get(NativeIdentityRoleFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native IdentityRole response (no 'rows' array) -- body: "
                    + snippet(json));
        }
        List<NativeIdentityRoleRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeIdentityRoleRecord toRecord(JsonNode r) {
        NativeIdentityRoleRecord rec = new NativeIdentityRoleRecord();
        rec.identityId = text(r, NativeIdentityRoleFields.IDENTITY_ID);
        rec.identityName = text(r, NativeIdentityRoleFields.IDENTITY_NAME);
        rec.roleId = text(r, NativeIdentityRoleFields.ROLE_ID);
        rec.roleName = text(r, NativeIdentityRoleFields.ROLE_NAME);
        rec.relationshipType = text(r, NativeIdentityRoleFields.RELATIONSHIP_TYPE);
        rec.assignmentId = text(r, NativeIdentityRoleFields.ASSIGNMENT_ID);
        rec.detectionAssignmentIds = text(r, NativeIdentityRoleFields.DETECTION_ASSIGNMENT_IDS);
        rec.comments = text(r, NativeIdentityRoleFields.COMMENTS);
        rec.futureAssignment = bool(r, NativeIdentityRoleFields.FUTURE_ASSIGNMENT);
        rec.promotedSoftPermit = bool(r, NativeIdentityRoleFields.PROMOTED_SOFT_PERMIT);
        rec.detectionDate = instant(r, NativeIdentityRoleFields.DETECTION_DATE);
        rec.targetsJson = json(r, NativeIdentityRoleFields.TARGETS);
        rec.srcSystem = text(r, NativeIdentityRoleFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeIdentityRoleFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeIdentityRoleFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeIdentityRoleFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeIdentityRoleFields.EXTRACTED_AT);
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
