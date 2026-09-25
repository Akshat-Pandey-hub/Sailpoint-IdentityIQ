package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the native Certification envelope; fails loudly on non-JSON / error / missing rows. */
final class NativeCertificationParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeCertificationFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeCertificationRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native Certification endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native Certification response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + error.path("type").asText("unknown")
                    + ": " + error.path("message").asText(""));
        }
        JsonNode rows = root.get(NativeCertificationFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native Certification response (no 'rows' array) -- body: "
                    + snippet(json));
        }
        List<NativeCertificationRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeCertificationRecord toRecord(JsonNode r) {
        NativeCertificationRecord rec = new NativeCertificationRecord();
        rec.sourceId = text(r, NativeCertificationFields.SOURCE_ID);
        rec.name = text(r, NativeCertificationFields.NAME);
        rec.certificationName = text(r, NativeCertificationFields.CERTIFICATION_NAME);
        rec.shortName = text(r, NativeCertificationFields.SHORT_NAME);
        rec.type = text(r, NativeCertificationFields.TYPE);
        rec.phase = text(r, NativeCertificationFields.PHASE);
        rec.comments = text(r, NativeCertificationFields.COMMENTS);
        rec.creator = text(r, NativeCertificationFields.CREATOR);
        rec.manager = text(r, NativeCertificationFields.MANAGER);
        rec.certificationGroupId = text(r, NativeCertificationFields.CERTIFICATION_GROUP_ID);
        rec.certificationGroupName = text(r, NativeCertificationFields.CERTIFICATION_GROUP_NAME);
        rec.certificationDefinitionId = text(r, NativeCertificationFields.CERTIFICATION_DEFINITION_ID);
        rec.groupDefinitionId = text(r, NativeCertificationFields.GROUP_DEFINITION_ID);
        rec.groupDefinitionName = text(r, NativeCertificationFields.GROUP_DEFINITION_NAME);
        rec.applicationId = text(r, NativeCertificationFields.APPLICATION_ID);
        rec.taskScheduleId = text(r, NativeCertificationFields.TASK_SCHEDULE_ID);
        rec.triggerId = text(r, NativeCertificationFields.TRIGGER_ID);
        rec.parentId = text(r, NativeCertificationFields.PARENT_ID);
        rec.complete = bool(r, NativeCertificationFields.COMPLETE);
        rec.expired = bool(r, NativeCertificationFields.EXPIRED);
        rec.continuous = bool(r, NativeCertificationFields.CONTINUOUS);
        rec.electronicallySigned = bool(r, NativeCertificationFields.ELECTRONICALLY_SIGNED);
        rec.signed = instant(r, NativeCertificationFields.SIGNED);
        rec.finished = instant(r, NativeCertificationFields.FINISHED);
        rec.activated = instant(r, NativeCertificationFields.ACTIVATED);
        rec.expiration = instant(r, NativeCertificationFields.EXPIRATION);
        rec.created = instant(r, NativeCertificationFields.CREATED);
        rec.modified = instant(r, NativeCertificationFields.MODIFIED);
        rec.totalItems = intOrNull(r, NativeCertificationFields.TOTAL_ITEMS);
        rec.completedItems = intOrNull(r, NativeCertificationFields.COMPLETED_ITEMS);
        rec.openItems = intOrNull(r, NativeCertificationFields.OPEN_ITEMS);
        rec.totalEntities = intOrNull(r, NativeCertificationFields.TOTAL_ENTITIES);
        rec.completedEntities = intOrNull(r, NativeCertificationFields.COMPLETED_ENTITIES);
        rec.openEntities = intOrNull(r, NativeCertificationFields.OPEN_ENTITIES);
        rec.percentComplete = intOrNull(r, NativeCertificationFields.PERCENT_COMPLETE);
        rec.certifiersJson = json(r, NativeCertificationFields.CERTIFIERS);
        rec.signOffHistoryJson = json(r, NativeCertificationFields.SIGN_OFF_HISTORY);
        rec.ownerId = text(r, NativeCertificationFields.OWNER_ID);
        rec.ownerName = text(r, NativeCertificationFields.OWNER_NAME);
        rec.approverRule = text(r, NativeCertificationFields.APPROVER_RULE);
        rec.automaticClosingDate = text(r, NativeCertificationFields.AUTOMATIC_CLOSING_DATE);
        rec.allowedStatusesJson = text(r, NativeCertificationFields.ALLOWED_STATUSES);
        rec.tagsJson = text(r, NativeCertificationFields.TAGS);
        rec.srcSystem = text(r, NativeCertificationFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeCertificationFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeCertificationFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeCertificationFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeCertificationFields.EXTRACTED_AT);
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
