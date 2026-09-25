package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the native CertificationItem envelope; fails loudly on non-JSON / error / missing rows. */
final class NativeCertificationItemParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeCertificationItemFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeCertificationItemRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native CertificationItem endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native CertificationItem response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + error.path("type").asText("unknown")
                    + ": " + error.path("message").asText(""));
        }
        JsonNode rows = root.get(NativeCertificationItemFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native CertificationItem response (no 'rows' array) -- body: "
                    + snippet(json));
        }
        List<NativeCertificationItemRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeCertificationItemRecord toRecord(JsonNode r) {
        NativeCertificationItemRecord rec = new NativeCertificationItemRecord();
        rec.sourceId = text(r, NativeCertificationItemFields.SOURCE_ID);
        rec.certificationId = text(r, NativeCertificationItemFields.CERTIFICATION_ID);
        rec.entityId = text(r, NativeCertificationItemFields.ENTITY_ID);
        rec.identity = text(r, NativeCertificationItemFields.IDENTITY);
        rec.type = text(r, NativeCertificationItemFields.TYPE);
        rec.subType = text(r, NativeCertificationItemFields.SUB_TYPE);
        rec.bundle = text(r, NativeCertificationItemFields.BUNDLE);
        rec.bundleAssignmentId = text(r, NativeCertificationItemFields.BUNDLE_ASSIGNMENT_ID);
        rec.exceptionApplication = text(r, NativeCertificationItemFields.EXCEPTION_APPLICATION);
        rec.exceptionAttributeName = text(r, NativeCertificationItemFields.EXCEPTION_ATTRIBUTE_NAME);
        rec.exceptionAttributeValue = text(r, NativeCertificationItemFields.EXCEPTION_ATTRIBUTE_VALUE);
        rec.exceptionPermissionTarget = text(r, NativeCertificationItemFields.EXCEPTION_PERMISSION_TARGET);
        rec.exceptionPermissionRight = text(r, NativeCertificationItemFields.EXCEPTION_PERMISSION_RIGHT);
        rec.accountGroup = text(r, NativeCertificationItemFields.ACCOUNT_GROUP);
        rec.phase = text(r, NativeCertificationItemFields.PHASE);
        rec.summaryStatus = text(r, NativeCertificationItemFields.SUMMARY_STATUS);
        rec.completed = instant(r, NativeCertificationItemFields.COMPLETED);
        rec.lastDecision = instant(r, NativeCertificationItemFields.LAST_DECISION);
        rec.expirationDate = instant(r, NativeCertificationItemFields.EXPIRATION_DATE);
        rec.finishedDate = instant(r, NativeCertificationItemFields.FINISHED_DATE);
        rec.iiqElevatedAccess = bool(r, NativeCertificationItemFields.IIQ_ELEVATED_ACCESS);
        rec.policyViolationId = text(r, NativeCertificationItemFields.POLICY_VIOLATION_ID);
        rec.roleAssignment = text(r, NativeCertificationItemFields.ROLE_ASSIGNMENT);
        rec.reviewed = bool(r, NativeCertificationItemFields.REVIEWED);
        rec.delegated = bool(r, NativeCertificationItemFields.DELEGATED);
        rec.actedUpon = bool(r, NativeCertificationItemFields.ACTED_UPON);
        rec.historical = bool(r, NativeCertificationItemFields.HISTORICAL);
        rec.expired = bool(r, NativeCertificationItemFields.EXPIRED);
        rec.targetId = text(r, NativeCertificationItemFields.TARGET_ID);
        rec.targetName = text(r, NativeCertificationItemFields.TARGET_NAME);
        rec.shortDescription = text(r, NativeCertificationItemFields.SHORT_DESCRIPTION);
        rec.violationSummary = text(r, NativeCertificationItemFields.VIOLATION_SUMMARY);
        rec.applicationNamesJson = json(r, NativeCertificationItemFields.APPLICATION_NAMES);
        rec.classificationNamesJson = json(r, NativeCertificationItemFields.CLASSIFICATION_NAMES);
        rec.actionStatus = text(r, NativeCertificationItemFields.ACTION_STATUS);
        rec.actionDecisionDate = instant(r, NativeCertificationItemFields.ACTION_DECISION_DATE);
        rec.actionDecisionCertificationId = text(r, NativeCertificationItemFields.ACTION_DECISION_CERTIFICATION_ID);
        rec.actionRemediationAction = text(r, NativeCertificationItemFields.ACTION_REMEDIATION_ACTION);
        rec.actionActorName = text(r, NativeCertificationItemFields.ACTION_ACTOR_NAME);
        rec.actionActorDisplayName = text(r, NativeCertificationItemFields.ACTION_ACTOR_DISPLAY_NAME);
        rec.actionComments = text(r, NativeCertificationItemFields.ACTION_COMMENTS);
        rec.actionCompletionComments = text(r, NativeCertificationItemFields.ACTION_COMPLETION_COMMENTS);
        rec.actionOwnerName = text(r, NativeCertificationItemFields.ACTION_OWNER_NAME);
        rec.actionMitigationExpiration = instant(r, NativeCertificationItemFields.ACTION_MITIGATION_EXPIRATION);
        rec.actionIsApproved = bool(r, NativeCertificationItemFields.ACTION_IS_APPROVED);
        rec.actionIsRemediation = bool(r, NativeCertificationItemFields.ACTION_IS_REMEDIATION);
        rec.actionIsMitigation = bool(r, NativeCertificationItemFields.ACTION_IS_MITIGATION);
        rec.actionIsDelegation = bool(r, NativeCertificationItemFields.ACTION_IS_DELEGATION);
        rec.actionIsRevokeAccount = bool(r, NativeCertificationItemFields.ACTION_IS_REVOKE_ACCOUNT);
        rec.actionIsAutoDecision = bool(r, NativeCertificationItemFields.ACTION_IS_AUTO_DECISION);
        rec.actionIsBulkCertified = bool(r, NativeCertificationItemFields.ACTION_IS_BULK_CERTIFIED);
        rec.ownerId = text(r, NativeCertificationItemFields.OWNER_ID);
        rec.ownerName = text(r, NativeCertificationItemFields.OWNER_NAME);
        rec.created = instant(r, NativeCertificationItemFields.CREATED);
        rec.modified = instant(r, NativeCertificationItemFields.MODIFIED);
        rec.srcSystem = text(r, NativeCertificationItemFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeCertificationItemFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeCertificationItemFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeCertificationItemFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeCertificationItemFields.EXTRACTED_AT);
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
