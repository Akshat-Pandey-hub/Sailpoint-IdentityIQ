package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeAuditEventRecord}s. Fails loudly on a
 * non-JSON body, a controlled {@code error} object, or a body without a {@code rows} array. Also exposes
 * {@code sourceCount} for the append-only full-scan guard.
 */
final class NativeAuditEventParser {

    private final ObjectMapper mapper = new ObjectMapper();

    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeAuditEventFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeAuditEventRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native AuditEvent endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native AuditEvent response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeAuditEventFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native AuditEvent response (no '"
                    + NativeAuditEventFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeAuditEventRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeAuditEventRecord toRecord(JsonNode r) {
        NativeAuditEventRecord rec = new NativeAuditEventRecord();
        rec.sourceId = text(r, NativeAuditEventFields.SOURCE_ID);
        rec.action = text(r, NativeAuditEventFields.ACTION);
        rec.auditSource = text(r, NativeAuditEventFields.AUDIT_SOURCE);
        rec.target = text(r, NativeAuditEventFields.TARGET);
        rec.application = text(r, NativeAuditEventFields.APPLICATION);
        rec.accountName = text(r, NativeAuditEventFields.ACCOUNT_NAME);
        rec.instance = text(r, NativeAuditEventFields.INSTANCE);
        rec.attributeName = text(r, NativeAuditEventFields.ATTRIBUTE_NAME);
        rec.attributeValue = text(r, NativeAuditEventFields.ATTRIBUTE_VALUE);
        rec.interfaceName = text(r, NativeAuditEventFields.INTERFACE_NAME);
        rec.serverHost = text(r, NativeAuditEventFields.SERVER_HOST);
        rec.clientHost = text(r, NativeAuditEventFields.CLIENT_HOST);
        rec.trackingId = text(r, NativeAuditEventFields.TRACKING_ID);
        rec.string1 = text(r, NativeAuditEventFields.STRING1);
        rec.string2 = text(r, NativeAuditEventFields.STRING2);
        rec.string3 = text(r, NativeAuditEventFields.STRING3);
        rec.string4 = text(r, NativeAuditEventFields.STRING4);
        rec.attributesJson = json(r, NativeAuditEventFields.ATTRIBUTES);
        rec.created = instant(r, NativeAuditEventFields.CREATED);
        rec.srcSystem = text(r, NativeAuditEventFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeAuditEventFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeAuditEventFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeAuditEventFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeAuditEventFields.EXTRACTED_AT);
        return rec;
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
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
