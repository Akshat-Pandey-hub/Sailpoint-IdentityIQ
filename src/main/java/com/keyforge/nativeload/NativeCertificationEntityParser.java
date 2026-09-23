package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the native CertificationEntity envelope; fails loudly on non-JSON / error / missing rows. */
final class NativeCertificationEntityParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeCertificationEntityFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeCertificationEntityRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native CertificationEntity endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native CertificationEntity response (not a JSON object) -- body: "
                    + snippet(json));
        }
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + error.path("type").asText("unknown")
                    + ": " + error.path("message").asText(""));
        }
        JsonNode rows = root.get(NativeCertificationEntityFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native CertificationEntity response (no 'rows' array) -- body: "
                    + snippet(json));
        }
        List<NativeCertificationEntityRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeCertificationEntityRecord toRecord(JsonNode r) {
        NativeCertificationEntityRecord rec = new NativeCertificationEntityRecord();
        rec.sourceId = text(r, NativeCertificationEntityFields.SOURCE_ID);
        rec.certificationId = text(r, NativeCertificationEntityFields.CERTIFICATION_ID);
        rec.identity = text(r, NativeCertificationEntityFields.IDENTITY);
        rec.application = text(r, NativeCertificationEntityFields.APPLICATION);
        rec.nativeIdentity = text(r, NativeCertificationEntityFields.NATIVE_IDENTITY);
        rec.accountGroup = text(r, NativeCertificationEntityFields.ACCOUNT_GROUP);
        rec.firstName = text(r, NativeCertificationEntityFields.FIRST_NAME);
        rec.lastName = text(r, NativeCertificationEntityFields.LAST_NAME);
        rec.fullName = text(r, NativeCertificationEntityFields.FULL_NAME);
        rec.referenceAttribute = text(r, NativeCertificationEntityFields.REFERENCE_ATTRIBUTE);
        rec.schemaObjectType = text(r, NativeCertificationEntityFields.SCHEMA_OBJECT_TYPE);
        rec.snapshotId = text(r, NativeCertificationEntityFields.SNAPSHOT_ID);
        rec.pendingCertification = text(r, NativeCertificationEntityFields.PENDING_CERTIFICATION);
        rec.type = text(r, NativeCertificationEntityFields.TYPE);
        rec.summaryStatus = text(r, NativeCertificationEntityFields.SUMMARY_STATUS);
        rec.entityDelegated = bool(r, NativeCertificationEntityFields.ENTITY_DELEGATED);
        rec.entityDelegationStatus = text(r, NativeCertificationEntityFields.ENTITY_DELEGATION_STATUS);
        rec.compositeScore = intOrNull(r, NativeCertificationEntityFields.COMPOSITE_SCORE);
        rec.targetId = text(r, NativeCertificationEntityFields.TARGET_ID);
        rec.targetName = text(r, NativeCertificationEntityFields.TARGET_NAME);
        rec.targetDisplayName = text(r, NativeCertificationEntityFields.TARGET_DISPLAY_NAME);
        rec.completed = instant(r, NativeCertificationEntityFields.COMPLETED);
        rec.created = instant(r, NativeCertificationEntityFields.CREATED);
        rec.modified = instant(r, NativeCertificationEntityFields.MODIFIED);
        rec.ownerId = text(r, NativeCertificationEntityFields.OWNER_ID);
        rec.ownerName = text(r, NativeCertificationEntityFields.OWNER_NAME);
        rec.actionStatus = text(r, NativeCertificationEntityFields.ACTION_STATUS);
        rec.actionDecisionDate = instant(r, NativeCertificationEntityFields.ACTION_DECISION_DATE);
        rec.actionRemediationAction = text(r, NativeCertificationEntityFields.ACTION_REMEDIATION_ACTION);
        rec.actionActorName = text(r, NativeCertificationEntityFields.ACTION_ACTOR_NAME);
        rec.actionActorDisplayName = text(r, NativeCertificationEntityFields.ACTION_ACTOR_DISPLAY_NAME);
        rec.actionComments = text(r, NativeCertificationEntityFields.ACTION_COMMENTS);
        rec.srcSystem = text(r, NativeCertificationEntityFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeCertificationEntityFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeCertificationEntityFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeCertificationEntityFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeCertificationEntityFields.EXTRACTED_AT);
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
