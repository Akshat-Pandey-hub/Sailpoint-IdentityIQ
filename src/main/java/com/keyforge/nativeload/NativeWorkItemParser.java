package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the native WorkItem envelope; fails loudly on non-JSON / error / missing rows. */
final class NativeWorkItemParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeWorkItemFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeWorkItemRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native WorkItem endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native WorkItem response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + error.path("type").asText("unknown")
                    + ": " + error.path("message").asText(""));
        }
        JsonNode rows = root.get(NativeWorkItemFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native WorkItem response (no 'rows' array) -- body: "
                    + snippet(json));
        }
        List<NativeWorkItemRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeWorkItemRecord toRecord(JsonNode r) {
        NativeWorkItemRecord rec = new NativeWorkItemRecord();
        rec.sourceId = text(r, NativeWorkItemFields.SOURCE_ID);
        rec.name = text(r, NativeWorkItemFields.NAME);
        rec.type = text(r, NativeWorkItemFields.TYPE);
        rec.state = text(r, NativeWorkItemFields.STATE);
        rec.level = text(r, NativeWorkItemFields.LEVEL);
        rec.requesterId = text(r, NativeWorkItemFields.REQUESTER_ID);
        rec.requesterName = text(r, NativeWorkItemFields.REQUESTER_NAME);
        rec.assigneeId = text(r, NativeWorkItemFields.ASSIGNEE_ID);
        rec.assigneeName = text(r, NativeWorkItemFields.ASSIGNEE_NAME);
        rec.ownerId = text(r, NativeWorkItemFields.OWNER_ID);
        rec.ownerName = text(r, NativeWorkItemFields.OWNER_NAME);
        rec.completer = text(r, NativeWorkItemFields.COMPLETER);
        rec.completionComments = text(r, NativeWorkItemFields.COMPLETION_COMMENTS);
        rec.handler = text(r, NativeWorkItemFields.HANDLER);
        rec.notificationName = text(r, NativeWorkItemFields.NOTIFICATION_NAME);
        rec.identityRequestId = text(r, NativeWorkItemFields.IDENTITY_REQUEST_ID);
        rec.targetId = text(r, NativeWorkItemFields.TARGET_ID);
        rec.targetName = text(r, NativeWorkItemFields.TARGET_NAME);
        rec.certificationId = text(r, NativeWorkItemFields.CERTIFICATION_ID);
        rec.certificationEntityId = text(r, NativeWorkItemFields.CERTIFICATION_ENTITY_ID);
        rec.certificationItemId = text(r, NativeWorkItemFields.CERTIFICATION_ITEM_ID);
        rec.entityType = text(r, NativeWorkItemFields.ENTITY_TYPE);
        rec.certificationRelated = bool(r, NativeWorkItemFields.CERTIFICATION_RELATED);
        rec.workflowCaseId = text(r, NativeWorkItemFields.WORKFLOW_CASE_ID);
        rec.workflowCaseName = text(r, NativeWorkItemFields.WORKFLOW_CASE_NAME);
        rec.expiration = instant(r, NativeWorkItemFields.EXPIRATION);
        rec.expirationDate = instant(r, NativeWorkItemFields.EXPIRATION_DATE);
        rec.notification = instant(r, NativeWorkItemFields.NOTIFICATION);
        rec.wakeUpDate = instant(r, NativeWorkItemFields.WAKE_UP_DATE);
        rec.escalationCount = intOrNull(r, NativeWorkItemFields.ESCALATION_COUNT);
        rec.reminders = intOrNull(r, NativeWorkItemFields.REMINDERS);
        rec.remindersSent = intOrNull(r, NativeWorkItemFields.REMINDERS_SENT);
        rec.expired = bool(r, NativeWorkItemFields.EXPIRED);
        rec.expirable = bool(r, NativeWorkItemFields.EXPIRABLE);
        rec.approvalSetItemCount = intOrNull(r, NativeWorkItemFields.APPROVAL_SET_ITEM_COUNT);
        rec.commentsJson = json(r, NativeWorkItemFields.COMMENTS);
        rec.signOffsJson = json(r, NativeWorkItemFields.SIGN_OFFS);
        rec.ownerHistoryJson = json(r, NativeWorkItemFields.OWNER_HISTORY);
        rec.approvalSetItemsJson = json(r, NativeWorkItemFields.APPROVAL_SET_ITEMS);
        rec.created = instant(r, NativeWorkItemFields.CREATED);
        rec.modified = instant(r, NativeWorkItemFields.MODIFIED);
        rec.srcSystem = text(r, NativeWorkItemFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeWorkItemFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeWorkItemFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeWorkItemFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeWorkItemFields.EXTRACTED_AT);
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
