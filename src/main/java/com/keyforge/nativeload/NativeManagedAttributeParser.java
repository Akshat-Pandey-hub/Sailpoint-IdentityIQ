package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeManagedAttributeRecord}s. Nested
 * structures are preserved verbatim as JSON strings for the {@code jsonb} columns; scalars are read by
 * the {@link NativeManagedAttributeFields} contract; ISO-8601 timestamps become {@link Instant} (a bad
 * timestamp becomes {@code null} rather than failing the row).
 */
final class NativeManagedAttributeParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeManagedAttributeRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native ManagedAttribute endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native ManagedAttribute response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeManagedAttributeFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native ManagedAttribute response (no '"
                    + NativeManagedAttributeFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeManagedAttributeRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    /** A short, safe excerpt of the raw body for diagnostics (never contains credentials). */
    private static String snippet(String json) {
        if (json == null || json.isEmpty()) {
            return "<empty>";
        }
        String s = json.strip().replaceAll("\\s+", " ");
        return s.length() <= 500 ? s : s.substring(0, 500) + "... (truncated)";
    }

    private NativeManagedAttributeRecord toRecord(JsonNode r) {
        NativeManagedAttributeRecord rec = new NativeManagedAttributeRecord();
        rec.sourceId = text(r, NativeManagedAttributeFields.SOURCE_ID);
        rec.name = text(r, NativeManagedAttributeFields.NAME);
        rec.value = text(r, NativeManagedAttributeFields.VALUE);
        rec.displayName = text(r, NativeManagedAttributeFields.DISPLAY_NAME);
        rec.displayableName = text(r, NativeManagedAttributeFields.DISPLAYABLE_NAME);
        rec.attribute = text(r, NativeManagedAttributeFields.ATTRIBUTE);
        rec.type = text(r, NativeManagedAttributeFields.TYPE);
        rec.uuid = text(r, NativeManagedAttributeFields.UUID);
        rec.referenceAttribute = text(r, NativeManagedAttributeFields.REFERENCE_ATTRIBUTE);
        rec.purview = text(r, NativeManagedAttributeFields.PURVIEW);
        rec.applicationId = text(r, NativeManagedAttributeFields.APPLICATION_ID);
        rec.applicationName = text(r, NativeManagedAttributeFields.APPLICATION_NAME);
        rec.instance = text(r, NativeManagedAttributeFields.INSTANCE);
        rec.nativeIdentity = text(r, NativeManagedAttributeFields.NATIVE_IDENTITY);

        rec.requestable = bool(r, NativeManagedAttributeFields.REQUESTABLE);
        rec.group = bool(r, NativeManagedAttributeFields.GROUP);
        rec.permission = bool(r, NativeManagedAttributeFields.PERMISSION);
        rec.uncorrelated = bool(r, NativeManagedAttributeFields.UNCORRELATED);
        rec.aggregated = bool(r, NativeManagedAttributeFields.AGGREGATED);
        rec.iiqElevatedAccess = bool(r, NativeManagedAttributeFields.IIQ_ELEVATED_ACCESS);

        rec.ownerId = text(r, NativeManagedAttributeFields.OWNER_ID);
        rec.ownerName = text(r, NativeManagedAttributeFields.OWNER_NAME);
        rec.description = text(r, NativeManagedAttributeFields.DESCRIPTION);

        rec.descriptionsJson = json(r, NativeManagedAttributeFields.DESCRIPTIONS);
        rec.permissionsJson = json(r, NativeManagedAttributeFields.PERMISSIONS);
        rec.targetPermissionsJson = json(r, NativeManagedAttributeFields.TARGET_PERMISSIONS);
        rec.inheritanceJson = json(r, NativeManagedAttributeFields.INHERITANCE);
        rec.associationsJson = json(r, NativeManagedAttributeFields.ASSOCIATIONS);
        rec.attributesJson = json(r, NativeManagedAttributeFields.ATTRIBUTES);

        rec.created = instant(r, NativeManagedAttributeFields.CREATED);
        rec.modified = instant(r, NativeManagedAttributeFields.MODIFIED);
        rec.lastRefresh = instant(r, NativeManagedAttributeFields.LAST_REFRESH);
        rec.lastTargetAggregation = instant(r, NativeManagedAttributeFields.LAST_TARGET_AGGREGATION);

        rec.srcSystem = text(r, NativeManagedAttributeFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeManagedAttributeFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeManagedAttributeFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeManagedAttributeFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeManagedAttributeFields.EXTRACTED_AT);
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
}
