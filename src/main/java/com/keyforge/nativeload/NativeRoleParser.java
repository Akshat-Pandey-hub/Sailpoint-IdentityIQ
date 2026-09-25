package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeRoleRecord}s. Fails loudly on a
 * non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array. A genuine {@code {"rows":[]}} returns empty. Nested structures are preserved verbatim as JSON
 * strings; ISO-8601 timestamps become {@link Instant} (a bad timestamp becomes {@code null}).
 */
final class NativeRoleParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeRoleRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native Role endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native Role response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeRoleFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native Role response (no '"
                    + NativeRoleFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeRoleRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeRoleRecord toRecord(JsonNode r) {
        NativeRoleRecord rec = new NativeRoleRecord();
        rec.sourceId = text(r, NativeRoleFields.SOURCE_ID);
        rec.name = text(r, NativeRoleFields.NAME);
        rec.displayName = text(r, NativeRoleFields.DISPLAY_NAME);
        rec.displayableName = text(r, NativeRoleFields.DISPLAYABLE_NAME);
        rec.fullName = text(r, NativeRoleFields.FULL_NAME);
        rec.description = text(r, NativeRoleFields.DESCRIPTION);
        rec.type = text(r, NativeRoleFields.TYPE);
        rec.assignmentId = text(r, NativeRoleFields.ASSIGNMENT_ID);

        rec.activityEnabled = bool(r, NativeRoleFields.ACTIVITY_ENABLED);
        rec.allowDuplicateAccounts = bool(r, NativeRoleFields.ALLOW_DUPLICATE_ACCOUNTS);
        rec.allowMultipleAssignments = bool(r, NativeRoleFields.ALLOW_MULTIPLE_ASSIGNMENTS);
        rec.autoPromotion = bool(r, NativeRoleFields.AUTO_PROMOTION);
        rec.differencable = bool(r, NativeRoleFields.DIFFERENCABLE);
        rec.iiqElevatedAccess = bool(r, NativeRoleFields.IIQ_ELEVATED_ACCESS);
        rec.mergeTemplates = bool(r, NativeRoleFields.MERGE_TEMPLATES);
        rec.orProfiles = bool(r, NativeRoleFields.OR_PROFILES);
        rec.pendingDelete = bool(r, NativeRoleFields.PENDING_DELETE);
        rec.hasSelector = bool(r, NativeRoleFields.HAS_SELECTOR);
        rec.riskScoreWeight = intOrNull(r, NativeRoleFields.RISK_SCORE_WEIGHT);

        rec.ownerId = text(r, NativeRoleFields.OWNER_ID);
        rec.ownerName = text(r, NativeRoleFields.OWNER_NAME);
        rec.activationDate = instant(r, NativeRoleFields.ACTIVATION_DATE);
        rec.deactivationDate = instant(r, NativeRoleFields.DEACTIVATION_DATE);

        rec.descriptionsJson = json(r, NativeRoleFields.DESCRIPTIONS);
        rec.attributesJson = json(r, NativeRoleFields.ATTRIBUTES);
        rec.roleTypeDefinition = text(r, NativeRoleFields.ROLE_TYPE_DEFINITION);
        rec.applicationsJson = text(r, NativeRoleFields.APPLICATIONS);
        rec.monitoredApplicationsJson = text(r, NativeRoleFields.MONITORED_APPLICATIONS);
        rec.scorecard = text(r, NativeRoleFields.SCORECARD);

        rec.created = instant(r, NativeRoleFields.CREATED);
        rec.modified = instant(r, NativeRoleFields.MODIFIED);

        rec.srcSystem = text(r, NativeRoleFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeRoleFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeRoleFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeRoleFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeRoleFields.EXTRACTED_AT);
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
