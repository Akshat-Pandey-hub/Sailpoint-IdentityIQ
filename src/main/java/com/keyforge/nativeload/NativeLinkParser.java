package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeLinkRecord}s. Fails loudly on a
 * non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array. A genuine {@code {"rows":[]}} returns empty. Nested structures are preserved verbatim as JSON
 * strings; ISO-8601 timestamps become {@link Instant} (a bad timestamp becomes {@code null}).
 */
final class NativeLinkParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeLinkRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native Link endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native Link response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeLinkFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native Link response (no '"
                    + NativeLinkFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeLinkRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeLinkRecord toRecord(JsonNode r) {
        NativeLinkRecord rec = new NativeLinkRecord();
        rec.sourceId = text(r, NativeLinkFields.SOURCE_ID);
        rec.uuid = text(r, NativeLinkFields.UUID);
        rec.nativeIdentity = text(r, NativeLinkFields.NATIVE_IDENTITY);
        rec.displayName = text(r, NativeLinkFields.DISPLAY_NAME);
        rec.displayableName = text(r, NativeLinkFields.DISPLAYABLE_NAME);
        rec.instance = text(r, NativeLinkFields.INSTANCE);
        rec.componentIds = text(r, NativeLinkFields.COMPONENT_IDS);
        rec.applicationId = text(r, NativeLinkFields.APPLICATION_ID);
        rec.applicationName = text(r, NativeLinkFields.APPLICATION_NAME);
        rec.identityId = text(r, NativeLinkFields.IDENTITY_ID);
        rec.identityName = text(r, NativeLinkFields.IDENTITY_NAME);

        rec.disabled = bool(r, NativeLinkFields.DISABLED);
        rec.locked = bool(r, NativeLinkFields.LOCKED);
        rec.composite = bool(r, NativeLinkFields.COMPOSITE);
        rec.manuallyCorrelated = bool(r, NativeLinkFields.MANUALLY_CORRELATED);
        rec.hasEntitlements = bool(r, NativeLinkFields.HAS_ENTITLEMENTS);
        rec.iiqDisabled = bool(r, NativeLinkFields.IIQ_DISABLED);
        rec.iiqLocked = bool(r, NativeLinkFields.IIQ_LOCKED);

        rec.permissionsJson = json(r, NativeLinkFields.PERMISSIONS);
        rec.targetPermissionsJson = json(r, NativeLinkFields.TARGET_PERMISSIONS);
        rec.attributesJson = json(r, NativeLinkFields.ATTRIBUTES);
        rec.entitlementAttributesJson = json(r, NativeLinkFields.ENTITLEMENT_ATTRIBUTES);

        rec.created = instant(r, NativeLinkFields.CREATED);
        rec.modified = instant(r, NativeLinkFields.MODIFIED);
        rec.lastRefresh = instant(r, NativeLinkFields.LAST_REFRESH);
        rec.lastTargetAggregation = instant(r, NativeLinkFields.LAST_TARGET_AGGREGATION);

        rec.srcSystem = text(r, NativeLinkFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeLinkFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeLinkFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeLinkFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeLinkFields.EXTRACTED_AT);
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
