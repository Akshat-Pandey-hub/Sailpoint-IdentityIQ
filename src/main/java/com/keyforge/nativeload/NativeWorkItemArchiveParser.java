package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeWorkItemArchiveRecord}s. Fails loudly on a
 * non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows} array
 * — an IIQ error can never masquerade as "0 extracted". A genuine {@code {"rows":[]}} returns empty. Nested
 * structures are preserved verbatim as JSON strings; ISO-8601 timestamps become {@link Instant} (a bad
 * timestamp becomes {@code null}).
 */
final class NativeWorkItemArchiveParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeWorkItemArchiveRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native WorkItemArchive endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native WorkItemArchive response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeWorkItemArchiveFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native WorkItemArchive response (no '"
                    + NativeWorkItemArchiveFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeWorkItemArchiveRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    int sourceCount(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native WorkItemArchive endpoint did not return JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject() || root.hasNonNull("error")
                || !root.path(NativeWorkItemArchiveFields.ROWS).isArray()) {
            throw new NativeImportException("Malformed native WorkItemArchive envelope -- body: " + snippet(json));
        }
        JsonNode count = root.get(NativeWorkItemArchiveFields.SOURCE_COUNT);
        return count == null || !count.canConvertToInt() ? -1 : count.asInt();
    }

    private NativeWorkItemArchiveRecord toRecord(JsonNode r) {
        NativeWorkItemArchiveRecord rec = new NativeWorkItemArchiveRecord();
        rec.sourceId = text(r, NativeWorkItemArchiveFields.SOURCE_ID);
        rec.workItemId = text(r, NativeWorkItemArchiveFields.WORK_ITEM_ID);
        rec.name = text(r, NativeWorkItemArchiveFields.NAME);
        rec.type = text(r, NativeWorkItemArchiveFields.TYPE);
        rec.state = text(r, NativeWorkItemArchiveFields.STATE);
        rec.level = text(r, NativeWorkItemArchiveFields.LEVEL);
        rec.requester = text(r, NativeWorkItemArchiveFields.REQUESTER);
        rec.assignee = text(r, NativeWorkItemArchiveFields.ASSIGNEE);
        rec.ownerName = text(r, NativeWorkItemArchiveFields.OWNER_NAME);
        rec.completer = text(r, NativeWorkItemArchiveFields.COMPLETER);
        rec.completionComments = text(r, NativeWorkItemArchiveFields.COMPLETION_COMMENTS);
        rec.signed = bool(r, NativeWorkItemArchiveFields.SIGNED);
        rec.targetClass = text(r, NativeWorkItemArchiveFields.TARGET_CLASS);
        rec.targetId = text(r, NativeWorkItemArchiveFields.TARGET_ID);
        rec.targetName = text(r, NativeWorkItemArchiveFields.TARGET_NAME);
        rec.identityRequestId = text(r, NativeWorkItemArchiveFields.IDENTITY_REQUEST_ID);
        rec.certificationId = text(r, NativeWorkItemArchiveFields.CERTIFICATION_ID);
        rec.certificationEntityId = text(r, NativeWorkItemArchiveFields.CERTIFICATION_ENTITY_ID);
        rec.certificationItemId = text(r, NativeWorkItemArchiveFields.CERTIFICATION_ITEM_ID);
        rec.entityType = text(r, NativeWorkItemArchiveFields.ENTITY_TYPE);

        rec.signOffsJson = json(r, NativeWorkItemArchiveFields.SIGN_OFFS);
        rec.commentsJson = json(r, NativeWorkItemArchiveFields.COMMENTS);
        rec.ownerHistoryJson = json(r, NativeWorkItemArchiveFields.OWNER_HISTORY);
        rec.systemAttributesJson = json(r, NativeWorkItemArchiveFields.SYSTEM_ATTRIBUTES);
        rec.attributesJson = json(r, NativeWorkItemArchiveFields.ATTRIBUTES);

        rec.created = instant(r, NativeWorkItemArchiveFields.CREATED);
        rec.modified = instant(r, NativeWorkItemArchiveFields.MODIFIED);
        rec.expiration = instant(r, NativeWorkItemArchiveFields.EXPIRATION);
        rec.archived = instant(r, NativeWorkItemArchiveFields.ARCHIVED);

        rec.srcSystem = text(r, NativeWorkItemArchiveFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeWorkItemArchiveFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeWorkItemArchiveFields.SRC_OBJECT_TYPE);
        rec.srcNaturalKey = text(r, NativeWorkItemArchiveFields.SRC_NATURAL_KEY);
        rec.srcEventTs = instant(r, NativeWorkItemArchiveFields.SRC_EVENT_TS);
        rec.extractionRunId = text(r, NativeWorkItemArchiveFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeWorkItemArchiveFields.EXTRACTED_AT);
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
