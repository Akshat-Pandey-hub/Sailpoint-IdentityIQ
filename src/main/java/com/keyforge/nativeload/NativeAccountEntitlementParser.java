package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeAccountEntitlementRecord}s. Fails loudly on
 * a non-JSON body, a controlled {@code error} object, or a body without a {@code rows} array. A genuine
 * {@code {"rows":[]}} returns empty (a Link page with no entitlements). ISO-8601 timestamps become
 * {@link Instant}. Also exposes {@code sourceCount} (total Links) and {@code returnedLinks} (Links in this
 * page) for the importer's Link-based pagination and incomplete-scan guard.
 */
final class NativeAccountEntitlementParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        return intField(json, NativeAccountEntitlementFields.SOURCE_COUNT, -1);
    }

    int returnedLinks(String json) {
        return intField(json, NativeAccountEntitlementFields.RETURNED_LINKS, -1);
    }

    private int intField(String json, String field, int def) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(field);
            return (n == null || n.isNull()) ? def : n.asInt(def);
        } catch (Exception e) {
            return def;
        }
    }

    List<NativeAccountEntitlementRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native AccountEntitlement endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native AccountEntitlement response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeAccountEntitlementFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native AccountEntitlement response (no '"
                    + NativeAccountEntitlementFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeAccountEntitlementRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeAccountEntitlementRecord toRecord(JsonNode r) {
        NativeAccountEntitlementRecord rec = new NativeAccountEntitlementRecord();
        rec.linkId = text(r, NativeAccountEntitlementFields.LINK_ID);
        rec.identityId = text(r, NativeAccountEntitlementFields.IDENTITY_ID);
        rec.identityName = text(r, NativeAccountEntitlementFields.IDENTITY_NAME);
        rec.applicationId = text(r, NativeAccountEntitlementFields.APPLICATION_ID);
        rec.applicationName = text(r, NativeAccountEntitlementFields.APPLICATION_NAME);
        rec.nativeIdentity = text(r, NativeAccountEntitlementFields.NATIVE_IDENTITY);
        rec.instance = text(r, NativeAccountEntitlementFields.INSTANCE);
        rec.attributeName = text(r, NativeAccountEntitlementFields.ATTRIBUTE_NAME);
        rec.attributeValue = text(r, NativeAccountEntitlementFields.ATTRIBUTE_VALUE);
        rec.srcSystem = text(r, NativeAccountEntitlementFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeAccountEntitlementFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeAccountEntitlementFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeAccountEntitlementFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeAccountEntitlementFields.EXTRACTED_AT);
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
