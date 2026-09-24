package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeSyslogEventRecord}s. Fails loudly on a
 * non-JSON body, a controlled {@code error} object, or a missing {@code rows} array. Exposes
 * {@code sourceCount} for the append-only complete-scan guard.
 */
final class NativeSyslogEventParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeSyslogEventFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeSyslogEventRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native SyslogEvent endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native SyslogEvent response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeSyslogEventFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native SyslogEvent response (no '"
                    + NativeSyslogEventFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeSyslogEventRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeSyslogEventRecord toRecord(JsonNode r) {
        NativeSyslogEventRecord rec = new NativeSyslogEventRecord();
        rec.sourceId = text(r, NativeSyslogEventFields.SOURCE_ID);
        rec.quickKey = text(r, NativeSyslogEventFields.QUICK_KEY);
        rec.eventLevel = text(r, NativeSyslogEventFields.EVENT_LEVEL);
        rec.server = text(r, NativeSyslogEventFields.SERVER);
        rec.username = text(r, NativeSyslogEventFields.USERNAME);
        rec.thread = text(r, NativeSyslogEventFields.THREAD);
        rec.lineNumber = text(r, NativeSyslogEventFields.LINE_NUMBER);
        rec.message = text(r, NativeSyslogEventFields.MESSAGE);
        rec.stacktrace = text(r, NativeSyslogEventFields.STACKTRACE);
        rec.created = instant(r, NativeSyslogEventFields.CREATED);
        rec.srcSystem = text(r, NativeSyslogEventFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeSyslogEventFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeSyslogEventFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeSyslogEventFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeSyslogEventFields.EXTRACTED_AT);
        return rec;
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
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
