package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeIdentityRecord}s. Nested structures
 * are preserved verbatim as JSON strings for the {@code jsonb} columns (lossless), scalars are read
 * by the {@link NativeIdentityFields} contract, and ISO-8601 timestamps are parsed to {@link Instant}
 * (a bad timestamp becomes {@code null} rather than failing the row).
 */
final class NativeIdentityParser {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parses one page envelope into its rows. A body that is not our envelope (no {@code rows} array),
     * or that carries a controlled {@code error} object, is surfaced as a {@link NativeImportException}
     * rather than being silently treated as an empty page — so an IIQ error can never masquerade as
     * "0 extracted". A genuine empty page ({@code {"rows":[]}}) returns an empty list without error.
     */
    List<NativeIdentityRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native Identity endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native Identity response (not a JSON object) -- body: "
                    + snippet(json));
        }
        // Controlled error envelope from the resource (or any body carrying an "error" object): surface it.
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            String type = error.path("type").asText("unknown");
            String message = error.path("message").asText("");
            throw new NativeImportException("IIQ plugin endpoint returned an error (status "
                    + root.path("status").asText("?") + "): " + type
                    + (message.isEmpty() ? "" : ": " + message));
        }
        JsonNode rows = root.get(NativeIdentityFields.ROWS);
        if (rows == null || !rows.isArray()) {
            // Not our envelope — do NOT report 0 silently; show what IIQ actually returned.
            throw new NativeImportException("Unexpected native Identity response (no '"
                    + NativeIdentityFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeIdentityRecord> out = new ArrayList<>();
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

    private NativeIdentityRecord toRecord(JsonNode r) {
        NativeIdentityRecord rec = new NativeIdentityRecord();
        rec.sourceId = text(r, NativeIdentityFields.SOURCE_ID);
        rec.name = text(r, NativeIdentityFields.NAME);
        rec.displayName = text(r, NativeIdentityFields.DISPLAY_NAME);
        rec.displayableName = text(r, NativeIdentityFields.DISPLAYABLE_NAME);
        rec.firstName = text(r, NativeIdentityFields.FIRST_NAME);
        rec.lastName = text(r, NativeIdentityFields.LAST_NAME);
        rec.email = text(r, NativeIdentityFields.EMAIL);
        rec.inactive = bool(r, NativeIdentityFields.INACTIVE);
        rec.type = text(r, NativeIdentityFields.TYPE);
        rec.correlated = bool(r, NativeIdentityFields.CORRELATED);
        rec.managerStatus = bool(r, NativeIdentityFields.MANAGER_STATUS);
        rec.managerId = text(r, NativeIdentityFields.MANAGER_ID);
        rec.managerName = text(r, NativeIdentityFields.MANAGER_NAME);
        rec.administratorId = text(r, NativeIdentityFields.ADMINISTRATOR_ID);
        rec.administratorName = text(r, NativeIdentityFields.ADMINISTRATOR_NAME);

        rec.accountsJson = json(r, NativeIdentityFields.ACCOUNTS);
        rec.assignedRolesJson = json(r, NativeIdentityFields.ASSIGNED_ROLES);
        rec.detectedRolesJson = json(r, NativeIdentityFields.DETECTED_ROLES);
        rec.roleAssignmentsJson = json(r, NativeIdentityFields.ROLE_ASSIGNMENTS);
        rec.roleDetectionsJson = json(r, NativeIdentityFields.ROLE_DETECTIONS);
        rec.capabilitiesJson = json(r, NativeIdentityFields.CAPABILITIES);
        rec.controlledScopesJson = json(r, NativeIdentityFields.CONTROLLED_SCOPES);
        rec.attributesJson = json(r, NativeIdentityFields.ATTRIBUTES);
        rec.score = text(r, NativeIdentityFields.SCORE);

        rec.created = instant(r, NativeIdentityFields.CREATED);
        rec.modified = instant(r, NativeIdentityFields.MODIFIED);
        rec.lastRefresh = instant(r, NativeIdentityFields.LAST_REFRESH);
        rec.lastLogin = instant(r, NativeIdentityFields.LAST_LOGIN);

        rec.srcSystem = text(r, NativeIdentityFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeIdentityFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeIdentityFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeIdentityFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeIdentityFields.EXTRACTED_AT);
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

    /** Serializes a nested node back to a JSON string for a jsonb column; {@code null} if absent. */
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
            return null; // never fail a row on a single bad timestamp
        }
    }
}
