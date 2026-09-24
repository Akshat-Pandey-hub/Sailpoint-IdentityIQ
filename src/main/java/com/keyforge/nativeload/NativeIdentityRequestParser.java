package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the native IdentityRequest envelope (requests with nested items + approvals); fails loudly on bad bodies. */
final class NativeIdentityRequestParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeIdentityRequestFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeIdentityRequestRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native IdentityRequest endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native IdentityRequest response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + error.path("type").asText("unknown")
                    + ": " + error.path("message").asText(""));
        }
        JsonNode rows = root.get(NativeIdentityRequestFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native IdentityRequest response (no 'rows' array) -- body: "
                    + snippet(json));
        }
        List<NativeIdentityRequestRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeIdentityRequestRecord toRecord(JsonNode r) {
        NativeIdentityRequestRecord rec = new NativeIdentityRequestRecord();
        rec.sourceId = text(r, NativeIdentityRequestFields.SOURCE_ID);
        if (rec.sourceId == null || rec.sourceId.isBlank()) {
            throw new NativeImportException("Native IdentityRequest row is missing its authoritative sourceId");
        }
        rec.name = text(r, NativeIdentityRequestFields.NAME);
        rec.type = text(r, NativeIdentityRequestFields.TYPE);
        rec.userFriendlyType = text(r, NativeIdentityRequestFields.USER_FRIENDLY_TYPE);
        rec.state = text(r, NativeIdentityRequestFields.STATE);
        rec.source = text(r, NativeIdentityRequestFields.SOURCE);
        rec.sourceObject = text(r, NativeIdentityRequestFields.SOURCE_OBJECT);
        rec.completionStatus = text(r, NativeIdentityRequestFields.COMPLETION_STATUS);
        rec.executionStatus = text(r, NativeIdentityRequestFields.EXECUTION_STATUS);
        rec.priority = text(r, NativeIdentityRequestFields.PRIORITY);
        rec.requesterId = text(r, NativeIdentityRequestFields.REQUESTER_ID);
        rec.requesterDisplayName = text(r, NativeIdentityRequestFields.REQUESTER_DISPLAY_NAME);
        rec.targetId = text(r, NativeIdentityRequestFields.TARGET_ID);
        rec.targetDisplayName = text(r, NativeIdentityRequestFields.TARGET_DISPLAY_NAME);
        rec.externalTicketId = text(r, NativeIdentityRequestFields.EXTERNAL_TICKET_ID);
        rec.processId = text(r, NativeIdentityRequestFields.PROCESS_ID);
        rec.taskResultId = text(r, NativeIdentityRequestFields.TASK_RESULT_ID);
        rec.executing = bool(r, NativeIdentityRequestFields.EXECUTING);
        rec.failure = bool(r, NativeIdentityRequestFields.FAILURE);
        rec.rejected = bool(r, NativeIdentityRequestFields.REJECTED);
        rec.successful = bool(r, NativeIdentityRequestFields.SUCCESSFUL);
        rec.terminated = bool(r, NativeIdentityRequestFields.TERMINATED);
        rec.incomplete = bool(r, NativeIdentityRequestFields.INCOMPLETE);
        rec.iiqOnly = bool(r, NativeIdentityRequestFields.IIQ_ONLY);
        rec.provisioningComplete = bool(r, NativeIdentityRequestFields.PROVISIONING_COMPLETE);
        rec.endDate = instant(r, NativeIdentityRequestFields.END_DATE);
        rec.verified = instant(r, NativeIdentityRequestFields.VERIFIED);
        rec.created = instant(r, NativeIdentityRequestFields.CREATED);
        rec.modified = instant(r, NativeIdentityRequestFields.MODIFIED);
        rec.ownerId = text(r, NativeIdentityRequestFields.OWNER_ID);
        rec.ownerName = text(r, NativeIdentityRequestFields.OWNER_NAME);
        rec.errorsJson = json(r, NativeIdentityRequestFields.ERRORS);
        rec.itemCount = intOrNull(r, NativeIdentityRequestFields.ITEM_COUNT);
        rec.approvalCount = intOrNull(r, NativeIdentityRequestFields.APPROVAL_COUNT);
        rec.srcSystem = text(r, NativeIdentityRequestFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeIdentityRequestFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeIdentityRequestFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeIdentityRequestFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeIdentityRequestFields.EXTRACTED_AT);

        JsonNode items = r.get(NativeIdentityRequestFields.ITEMS);
        if (items != null && items.isArray()) {
            for (JsonNode it : items) {
                rec.items.add(toItem(it, rec));
            }
        }
        JsonNode approvals = r.get(NativeIdentityRequestFields.APPROVALS);
        if (approvals != null && approvals.isArray()) {
            for (JsonNode ap : approvals) {
                rec.approvals.add(toApproval(ap, rec));
            }
        }
        return rec;
    }

    private NativeIdentityRequestItemRecord toItem(JsonNode it, NativeIdentityRequestRecord req) {
        NativeIdentityRequestItemRecord i = new NativeIdentityRequestItemRecord();
        i.sourceId = text(it, NativeIdentityRequestFields.ITEM_SOURCE_ID);
        i.requestSourceId = text(it, NativeIdentityRequestFields.ITEM_REQUEST_SOURCE_ID);
        i.requestName = text(it, NativeIdentityRequestFields.ITEM_REQUEST_NAME);
        i.application = text(it, NativeIdentityRequestFields.ITEM_APPLICATION);
        i.attributeName = text(it, NativeIdentityRequestFields.ITEM_ATTRIBUTE_NAME);
        i.attributeValue = text(it, NativeIdentityRequestFields.ITEM_ATTRIBUTE_VALUE);
        i.operation = text(it, NativeIdentityRequestFields.ITEM_OPERATION);
        i.managedAttributeType = text(it, NativeIdentityRequestFields.ITEM_MANAGED_ATTRIBUTE_TYPE);
        i.assignmentId = text(it, NativeIdentityRequestFields.ITEM_ASSIGNMENT_ID);
        i.nativeIdentity = text(it, NativeIdentityRequestFields.ITEM_NATIVE_IDENTITY);
        i.instance = text(it, NativeIdentityRequestFields.ITEM_INSTANCE);
        i.approverName = text(it, NativeIdentityRequestFields.ITEM_APPROVER_NAME);
        i.approvalState = text(it, NativeIdentityRequestFields.ITEM_APPROVAL_STATE);
        i.approved = bool(it, NativeIdentityRequestFields.ITEM_APPROVED);
        i.approvalComplete = bool(it, NativeIdentityRequestFields.ITEM_APPROVAL_COMPLETE);
        i.rejected = bool(it, NativeIdentityRequestFields.ITEM_REJECTED);
        i.provisioningState = text(it, NativeIdentityRequestFields.ITEM_PROVISIONING_STATE);
        i.provisioningEngine = text(it, NativeIdentityRequestFields.ITEM_PROVISIONING_ENGINE);
        i.provisioningRequestId = text(it, NativeIdentityRequestFields.ITEM_PROVISIONING_REQUEST_ID);
        i.provisioningComplete = bool(it, NativeIdentityRequestFields.ITEM_PROVISIONING_COMPLETE);
        i.provisioningFailed = bool(it, NativeIdentityRequestFields.ITEM_PROVISIONING_FAILED);
        i.compilationStatus = text(it, NativeIdentityRequestFields.ITEM_COMPILATION_STATUS);
        i.ownerName = text(it, NativeIdentityRequestFields.ITEM_OWNER_NAME);
        i.requesterComments = text(it, NativeIdentityRequestFields.ITEM_REQUESTER_COMMENTS);
        i.expansion = bool(it, NativeIdentityRequestFields.ITEM_EXPANSION);
        i.expansionCause = text(it, NativeIdentityRequestFields.ITEM_EXPANSION_CAUSE);
        i.expansionInfo = text(it, NativeIdentityRequestFields.ITEM_EXPANSION_INFO);
        i.retries = intOrNull(it, NativeIdentityRequestFields.ITEM_RETRIES);
        i.iiq = bool(it, NativeIdentityRequestFields.ITEM_IIQ);
        i.startDate = instant(it, NativeIdentityRequestFields.ITEM_START_DATE);
        i.endDate = instant(it, NativeIdentityRequestFields.ITEM_END_DATE);
        i.created = instant(it, NativeIdentityRequestFields.ITEM_CREATED);
        i.modified = instant(it, NativeIdentityRequestFields.ITEM_MODIFIED);
        if (i.requestSourceId == null) {
            i.requestSourceId = req.sourceId;
        }
        i.srcSystem = req.srcSystem;
        i.srcInterface = req.srcInterface;
        i.srcObjectType = "sailpoint.object.IdentityRequestItem";
        i.extractionRunId = req.extractionRunId;
        return i;
    }

    private NativeIdentityRequestApprovalRecord toApproval(JsonNode ap, NativeIdentityRequestRecord req) {
        NativeIdentityRequestApprovalRecord a = new NativeIdentityRequestApprovalRecord();
        a.requestSourceId = text(ap, NativeIdentityRequestFields.APPROVAL_REQUEST_SOURCE_ID);
        a.requestName = text(ap, NativeIdentityRequestFields.APPROVAL_REQUEST_NAME);
        a.workItemId = text(ap, NativeIdentityRequestFields.APPROVAL_WORK_ITEM_ID);
        a.workItemType = text(ap, NativeIdentityRequestFields.APPROVAL_WORK_ITEM_TYPE);
        a.owner = text(ap, NativeIdentityRequestFields.APPROVAL_OWNER);
        a.ownerId = text(ap, NativeIdentityRequestFields.APPROVAL_OWNER_ID);
        a.completer = text(ap, NativeIdentityRequestFields.APPROVAL_COMPLETER);
        a.approved = bool(ap, NativeIdentityRequestFields.APPROVAL_APPROVED);
        a.state = text(ap, NativeIdentityRequestFields.APPROVAL_STATE);
        a.stateKey = text(ap, NativeIdentityRequestFields.APPROVAL_STATE_KEY);
        a.typeKey = text(ap, NativeIdentityRequestFields.APPROVAL_TYPE_KEY);
        a.startDate = instant(ap, NativeIdentityRequestFields.APPROVAL_START_DATE);
        a.endDate = instant(ap, NativeIdentityRequestFields.APPROVAL_END_DATE);
        a.approvalItemCount = intOrNull(ap, NativeIdentityRequestFields.APPROVAL_ITEM_COUNT);
        a.approvalIndex = intOrNull(ap, NativeIdentityRequestFields.APPROVAL_INDEX);
        a.commentsJson = json(ap, NativeIdentityRequestFields.APPROVAL_COMMENTS);
        a.signOffJson = json(ap, NativeIdentityRequestFields.APPROVAL_SIGN_OFF);
        if (a.requestSourceId == null) {
            a.requestSourceId = req.sourceId;
        }
        a.srcSystem = req.srcSystem;
        a.srcInterface = req.srcInterface;
        a.srcObjectType = "sailpoint.object.WorkItem";
        a.extractionRunId = req.extractionRunId;
        return a;
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
