package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the native ProvisioningTransaction envelope (txns with nested items); fails loudly on bad bodies. */
final class NativeProvisioningTxnParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeProvisioningTxnFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeProvisioningTxnRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native ProvisioningTransaction endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native ProvisioningTransaction response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + error.path("type").asText("unknown")
                    + ": " + error.path("message").asText(""));
        }
        JsonNode rows = root.get(NativeProvisioningTxnFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native ProvisioningTransaction response (no 'rows' array) -- body: "
                    + snippet(json));
        }
        List<NativeProvisioningTxnRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeProvisioningTxnRecord toRecord(JsonNode r) {
        NativeProvisioningTxnRecord rec = new NativeProvisioningTxnRecord();
        rec.sourceId = text(r, NativeProvisioningTxnFields.SOURCE_ID);
        if (rec.sourceId == null || rec.sourceId.isBlank()) {
            throw new NativeImportException("Native ProvisioningTransaction row is missing its authoritative sourceId");
        }
        rec.name = text(r, NativeProvisioningTxnFields.NAME);
        rec.operation = text(r, NativeProvisioningTxnFields.OPERATION);
        rec.type = text(r, NativeProvisioningTxnFields.TYPE);
        rec.status = text(r, NativeProvisioningTxnFields.STATUS);
        rec.source = text(r, NativeProvisioningTxnFields.SOURCE);
        rec.integration = text(r, NativeProvisioningTxnFields.INTEGRATION);
        rec.forced = bool(r, NativeProvisioningTxnFields.FORCED);
        rec.identityName = text(r, NativeProvisioningTxnFields.IDENTITY_NAME);
        rec.identityDisplayName = text(r, NativeProvisioningTxnFields.IDENTITY_DISPLAY_NAME);
        rec.applicationName = text(r, NativeProvisioningTxnFields.APPLICATION_NAME);
        rec.nativeIdentity = text(r, NativeProvisioningTxnFields.NATIVE_IDENTITY);
        rec.accountDisplayName = text(r, NativeProvisioningTxnFields.ACCOUNT_DISPLAY_NAME);
        rec.certificationId = text(r, NativeProvisioningTxnFields.CERTIFICATION_ID);
        rec.certificationName = text(r, NativeProvisioningTxnFields.CERTIFICATION_NAME);
        rec.accessRequestId = text(r, NativeProvisioningTxnFields.ACCESS_REQUEST_ID);
        rec.waitWorkItemId = text(r, NativeProvisioningTxnFields.WAIT_WORK_ITEM_ID);
        rec.manualWorkItemId = text(r, NativeProvisioningTxnFields.MANUAL_WORK_ITEM_ID);
        rec.ticketId = text(r, NativeProvisioningTxnFields.TICKET_ID);
        rec.retryRequestId = text(r, NativeProvisioningTxnFields.RETRY_REQUEST_ID);
        rec.lastRetry = instant(r, NativeProvisioningTxnFields.LAST_RETRY);
        rec.retryCount = intOrNull(r, NativeProvisioningTxnFields.RETRY_COUNT);
        rec.timedOut = bool(r, NativeProvisioningTxnFields.TIMED_OUT);
        rec.filtered = bool(r, NativeProvisioningTxnFields.FILTERED);
        rec.planResultStatus = text(r, NativeProvisioningTxnFields.PLAN_RESULT_STATUS);
        rec.planResultRequestId = text(r, NativeProvisioningTxnFields.PLAN_RESULT_REQUEST_ID);
        rec.planResultErrorsJson = json(r, NativeProvisioningTxnFields.PLAN_RESULT_ERRORS);
        rec.accountRequestOperation = text(r, NativeProvisioningTxnFields.ACCOUNT_REQUEST_OPERATION);
        rec.requestId = text(r, NativeProvisioningTxnFields.REQUEST_ID);
        rec.itemCount = intOrNull(r, NativeProvisioningTxnFields.ITEM_COUNT);
        rec.ownerId = text(r, NativeProvisioningTxnFields.OWNER_ID);
        rec.ownerName = text(r, NativeProvisioningTxnFields.OWNER_NAME);
        rec.created = instant(r, NativeProvisioningTxnFields.CREATED);
        rec.modified = instant(r, NativeProvisioningTxnFields.MODIFIED);
        rec.srcSystem = text(r, NativeProvisioningTxnFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeProvisioningTxnFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeProvisioningTxnFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeProvisioningTxnFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeProvisioningTxnFields.EXTRACTED_AT);

        JsonNode items = r.get(NativeProvisioningTxnFields.ITEMS);
        if (items != null && items.isArray()) {
            for (JsonNode it : items) {
                rec.items.add(toItem(it, rec));
            }
        }
        return rec;
    }

    private NativeProvisioningItemRecord toItem(JsonNode it, NativeProvisioningTxnRecord txn) {
        NativeProvisioningItemRecord i = new NativeProvisioningItemRecord();
        i.txnSourceId = text(it, NativeProvisioningTxnFields.ITEM_TXN_SOURCE_ID);
        i.identityName = text(it, NativeProvisioningTxnFields.ITEM_IDENTITY_NAME);
        i.itemType = text(it, NativeProvisioningTxnFields.ITEM_TYPE);
        i.operation = text(it, NativeProvisioningTxnFields.ITEM_OPERATION);
        i.applicationName = text(it, NativeProvisioningTxnFields.ITEM_APPLICATION_NAME);
        i.nativeIdentity = text(it, NativeProvisioningTxnFields.ITEM_NATIVE_IDENTITY);
        i.instance = text(it, NativeProvisioningTxnFields.ITEM_INSTANCE);
        i.accountOperation = text(it, NativeProvisioningTxnFields.ITEM_ACCOUNT_OPERATION);
        i.name = text(it, NativeProvisioningTxnFields.ITEM_NAME);
        i.value = text(it, NativeProvisioningTxnFields.ITEM_VALUE);
        i.valueJson = json(it, NativeProvisioningTxnFields.ITEM_VALUE_JSON);
        i.assignmentId = text(it, NativeProvisioningTxnFields.ITEM_ASSIGNMENT_ID);
        i.assignment = bool(it, NativeProvisioningTxnFields.ITEM_ASSIGNMENT);
        i.permissionTarget = text(it, NativeProvisioningTxnFields.ITEM_PERMISSION_TARGET);
        i.permissionRights = text(it, NativeProvisioningTxnFields.ITEM_PERMISSION_RIGHTS);
        i.requestId = text(it, NativeProvisioningTxnFields.ITEM_REQUEST_ID);
        i.itemIndex = intOrNull(it, NativeProvisioningTxnFields.ITEM_INDEX);
        if (i.txnSourceId == null) {
            i.txnSourceId = txn.sourceId;
        }
        i.srcSystem = txn.srcSystem;
        i.srcInterface = txn.srcInterface;
        i.srcObjectType = "PERMISSION".equals(i.itemType)
                ? "sailpoint.object.ProvisioningPlan$PermissionRequest"
                : "sailpoint.object.ProvisioningPlan$AttributeRequest";
        i.extractionRunId = txn.extractionRunId;
        return i;
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
