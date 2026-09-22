package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeWorkgroupRecord}s. Fails loudly on a
 * non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array. A genuine {@code {"rows":[]}} returns empty. Nested structures are preserved verbatim as JSON
 * strings; ISO-8601 timestamps become {@link Instant} (a bad timestamp becomes {@code null}).
 */
final class NativeWorkgroupParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeWorkgroupRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native Workgroup endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native Workgroup response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeWorkgroupFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native Workgroup response (no '"
                    + NativeWorkgroupFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeWorkgroupRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeWorkgroupRecord toRecord(JsonNode r) {
        NativeWorkgroupRecord rec = new NativeWorkgroupRecord();
        rec.sourceId = text(r, NativeWorkgroupFields.SOURCE_ID);
        rec.name = text(r, NativeWorkgroupFields.NAME);
        rec.displayName = text(r, NativeWorkgroupFields.DISPLAY_NAME);
        rec.displayableName = text(r, NativeWorkgroupFields.DISPLAYABLE_NAME);
        rec.email = text(r, NativeWorkgroupFields.EMAIL);
        rec.type = text(r, NativeWorkgroupFields.TYPE);
        rec.description = text(r, NativeWorkgroupFields.DESCRIPTION);
        rec.notificationOption = text(r, NativeWorkgroupFields.NOTIFICATION_OPTION);
        rec.inactive = bool(r, NativeWorkgroupFields.INACTIVE);
        rec.workgroup = bool(r, NativeWorkgroupFields.WORKGROUP);
        rec.ownerId = text(r, NativeWorkgroupFields.OWNER_ID);
        rec.ownerName = text(r, NativeWorkgroupFields.OWNER_NAME);

        rec.capabilitiesJson = json(r, NativeWorkgroupFields.CAPABILITIES);
        rec.attributesJson = json(r, NativeWorkgroupFields.ATTRIBUTES);

        rec.created = instant(r, NativeWorkgroupFields.CREATED);
        rec.modified = instant(r, NativeWorkgroupFields.MODIFIED);

        rec.srcSystem = text(r, NativeWorkgroupFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeWorkgroupFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeWorkgroupFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeWorkgroupFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeWorkgroupFields.EXTRACTED_AT);
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
