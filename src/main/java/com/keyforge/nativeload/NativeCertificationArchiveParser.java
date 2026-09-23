package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeCertificationArchiveRecord}s. Fails loudly on
 * a non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows} array
 * — an IIQ error can never masquerade as "0 extracted". A genuine {@code {"rows":[]}} returns empty. Nested
 * structures are preserved verbatim as JSON strings; ISO-8601 timestamps become {@link Instant} (a bad
 * timestamp becomes {@code null}).
 */
final class NativeCertificationArchiveParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeCertificationArchiveRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native CertificationArchive endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native CertificationArchive response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeCertificationArchiveFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native CertificationArchive response (no '"
                    + NativeCertificationArchiveFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeCertificationArchiveRecord> out = new ArrayList<>();
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
            throw new NativeImportException("Native CertificationArchive endpoint did not return JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject() || root.hasNonNull("error")
                || !root.path(NativeCertificationArchiveFields.ROWS).isArray()) {
            throw new NativeImportException("Malformed native CertificationArchive envelope -- body: " + snippet(json));
        }
        JsonNode count = root.get(NativeCertificationArchiveFields.SOURCE_COUNT);
        return count == null || !count.canConvertToInt() ? -1 : count.asInt();
    }

    private NativeCertificationArchiveRecord toRecord(JsonNode r) {
        NativeCertificationArchiveRecord rec = new NativeCertificationArchiveRecord();
        rec.sourceId = text(r, NativeCertificationArchiveFields.SOURCE_ID);
        rec.name = text(r, NativeCertificationArchiveFields.NAME);
        rec.certificationId = text(r, NativeCertificationArchiveFields.CERTIFICATION_ID);
        rec.certificationGroupId = text(r, NativeCertificationArchiveFields.CERTIFICATION_GROUP_ID);
        rec.creatorName = text(r, NativeCertificationArchiveFields.CREATOR_NAME);
        rec.ownerName = text(r, NativeCertificationArchiveFields.OWNER_NAME);
        rec.comments = text(r, NativeCertificationArchiveFields.COMMENTS);

        rec.childCertificationIdsJson = json(r, NativeCertificationArchiveFields.CHILD_CERTIFICATION_IDS);
        rec.archiveXml = text(r, NativeCertificationArchiveFields.ARCHIVE_XML);

        rec.signed = instant(r, NativeCertificationArchiveFields.SIGNED);
        rec.expiration = instant(r, NativeCertificationArchiveFields.EXPIRATION);
        rec.created = instant(r, NativeCertificationArchiveFields.CREATED);
        rec.modified = instant(r, NativeCertificationArchiveFields.MODIFIED);

        rec.srcSystem = text(r, NativeCertificationArchiveFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeCertificationArchiveFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeCertificationArchiveFields.SRC_OBJECT_TYPE);
        rec.srcNaturalKey = text(r, NativeCertificationArchiveFields.SRC_NATURAL_KEY);
        rec.srcEventTs = instant(r, NativeCertificationArchiveFields.SRC_EVENT_TS);
        rec.extractionRunId = text(r, NativeCertificationArchiveFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeCertificationArchiveFields.EXTRACTED_AT);
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
