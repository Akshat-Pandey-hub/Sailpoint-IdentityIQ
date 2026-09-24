package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeTaskScheduleRecord}s. Fails loudly on a
 * non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array — an IIQ error can never masquerade as "0 extracted". A genuine {@code {"rows":[]}} returns
 * empty. Nested structures are preserved verbatim as JSON strings; ISO-8601 timestamps become
 * {@link Instant} (a bad timestamp becomes {@code null}).
 */
final class NativeTaskScheduleParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeTaskScheduleRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native TaskSchedule endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native TaskSchedule response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeTaskScheduleFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native TaskSchedule response (no '"
                    + NativeTaskScheduleFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeTaskScheduleRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeTaskScheduleRecord toRecord(JsonNode r) {
        NativeTaskScheduleRecord rec = new NativeTaskScheduleRecord();
        rec.sourceId = text(r, NativeTaskScheduleFields.SOURCE_ID);
        rec.name = text(r, NativeTaskScheduleFields.NAME);
        rec.description = text(r, NativeTaskScheduleFields.DESCRIPTION);
        rec.definitionName = text(r, NativeTaskScheduleFields.DEFINITION_NAME);
        rec.state = text(r, NativeTaskScheduleFields.STATE);
        rec.newState = text(r, NativeTaskScheduleFields.NEW_STATE);
        rec.launcher = text(r, NativeTaskScheduleFields.LAUNCHER);
        rec.host = text(r, NativeTaskScheduleFields.HOST);
        rec.lastLaunchError = text(r, NativeTaskScheduleFields.LAST_LAUNCH_ERROR);
        rec.deleteOnFinish = bool(r, NativeTaskScheduleFields.DELETE_ON_FINISH);
        rec.lastExecution = instant(r, NativeTaskScheduleFields.LAST_EXECUTION);
        rec.nextExecution = instant(r, NativeTaskScheduleFields.NEXT_EXECUTION);
        rec.nextActualExecution = instant(r, NativeTaskScheduleFields.NEXT_ACTUAL_EXECUTION);
        rec.resumeDate = instant(r, NativeTaskScheduleFields.RESUME_DATE);
        rec.cronExpressionsJson = json(r, NativeTaskScheduleFields.CRON_EXPRESSIONS);
        rec.argumentsJson = json(r, NativeTaskScheduleFields.ARGUMENTS);
        rec.created = instant(r, NativeTaskScheduleFields.CREATED);
        rec.modified = instant(r, NativeTaskScheduleFields.MODIFIED);
        rec.srcSystem = text(r, NativeTaskScheduleFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeTaskScheduleFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeTaskScheduleFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeTaskScheduleFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeTaskScheduleFields.EXTRACTED_AT);
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
