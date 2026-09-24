package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeTaskResultRecord}s. Fails loudly on a
 * non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array — an IIQ error can never masquerade as "0 extracted". A genuine {@code {"rows":[]}} returns
 * empty. Nested structures are preserved verbatim as JSON strings; ISO-8601 timestamps become
 * {@link Instant} (a bad timestamp becomes {@code null}).
 */
final class NativeTaskResultParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeTaskResultRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native TaskResult endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native TaskResult response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeTaskResultFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native TaskResult response (no '"
                    + NativeTaskResultFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeTaskResultRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeTaskResultRecord toRecord(JsonNode r) {
        NativeTaskResultRecord rec = new NativeTaskResultRecord();
        rec.sourceId = text(r, NativeTaskResultFields.SOURCE_ID);
        rec.name = text(r, NativeTaskResultFields.NAME);
        rec.type = text(r, NativeTaskResultFields.TYPE);
        rec.completionStatus = text(r, NativeTaskResultFields.COMPLETION_STATUS);
        rec.definitionName = text(r, NativeTaskResultFields.DEFINITION_NAME);
        rec.launcher = text(r, NativeTaskResultFields.LAUNCHER);
        rec.host = text(r, NativeTaskResultFields.HOST);
        rec.targetName = text(r, NativeTaskResultFields.TARGET_NAME);
        rec.targetClass = text(r, NativeTaskResultFields.TARGET_CLASS);
        rec.targetId = text(r, NativeTaskResultFields.TARGET_ID);
        rec.schedule = text(r, NativeTaskResultFields.SCHEDULE);
        rec.progress = text(r, NativeTaskResultFields.PROGRESS);
        rec.percentComplete = intOrNull(r, NativeTaskResultFields.PERCENT_COMPLETE);
        rec.runLength = intOrNull(r, NativeTaskResultFields.RUN_LENGTH);
        rec.pendingSignoffs = intOrNull(r, NativeTaskResultFields.PENDING_SIGNOFFS);
        rec.partitioned = bool(r, NativeTaskResultFields.PARTITIONED);
        rec.terminateRequested = bool(r, NativeTaskResultFields.TERMINATE_REQUESTED);
        rec.complete = bool(r, NativeTaskResultFields.COMPLETE);
        rec.launched = instant(r, NativeTaskResultFields.LAUNCHED);
        rec.completed = instant(r, NativeTaskResultFields.COMPLETED);
        rec.expiration = instant(r, NativeTaskResultFields.EXPIRATION);
        rec.verified = instant(r, NativeTaskResultFields.VERIFIED);
        rec.messagesJson = json(r, NativeTaskResultFields.MESSAGES);
        rec.attributesJson = json(r, NativeTaskResultFields.ATTRIBUTES);
        rec.created = instant(r, NativeTaskResultFields.CREATED);
        rec.modified = instant(r, NativeTaskResultFields.MODIFIED);
        rec.srcSystem = text(r, NativeTaskResultFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeTaskResultFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeTaskResultFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeTaskResultFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeTaskResultFields.EXTRACTED_AT);
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
