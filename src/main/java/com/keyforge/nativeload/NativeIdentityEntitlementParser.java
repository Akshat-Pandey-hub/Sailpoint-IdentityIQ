package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeIdentityEntitlementRecord}s. Fails loudly on
 * a non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array. A genuine {@code {"rows":[]}} returns empty. {@code valueList} is preserved verbatim as a JSON
 * string; ISO-8601 timestamps become {@link Instant} (a bad timestamp becomes {@code null}).
 */
final class NativeIdentityEntitlementParser {

    private final ObjectMapper mapper = new ObjectMapper();

    /** @return the authoritative {@code sourceCount} from the envelope, or {@code -1} when absent. */
    int sourceCount(String json) {
        try {
            JsonNode root = mapper.readTree(json == null ? "" : json);
            JsonNode n = root == null ? null : root.get(NativeIdentityEntitlementFields.SOURCE_COUNT);
            return (n == null || n.isNull()) ? -1 : n.asInt(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    List<NativeIdentityEntitlementRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native IdentityEntitlement endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native IdentityEntitlement response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeIdentityEntitlementFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native IdentityEntitlement response (no '"
                    + NativeIdentityEntitlementFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeIdentityEntitlementRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeIdentityEntitlementRecord toRecord(JsonNode r) {
        NativeIdentityEntitlementRecord rec = new NativeIdentityEntitlementRecord();
        rec.sourceId = text(r, NativeIdentityEntitlementFields.SOURCE_ID);
        rec.identityId = text(r, NativeIdentityEntitlementFields.IDENTITY_ID);
        rec.identityName = text(r, NativeIdentityEntitlementFields.IDENTITY_NAME);
        rec.applicationId = text(r, NativeIdentityEntitlementFields.APPLICATION_ID);
        rec.applicationName = text(r, NativeIdentityEntitlementFields.APPLICATION_NAME);
        rec.nativeIdentity = text(r, NativeIdentityEntitlementFields.NATIVE_IDENTITY);
        rec.instance = text(r, NativeIdentityEntitlementFields.INSTANCE);
        rec.attributeName = text(r, NativeIdentityEntitlementFields.ATTRIBUTE_NAME);
        rec.attributeValue = text(r, NativeIdentityEntitlementFields.ATTRIBUTE_VALUE);
        rec.valueListJson = json(r, NativeIdentityEntitlementFields.VALUE_LIST);
        rec.type = text(r, NativeIdentityEntitlementFields.TYPE);
        rec.displayName = text(r, NativeIdentityEntitlementFields.DISPLAY_NAME);
        rec.annotation = text(r, NativeIdentityEntitlementFields.ANNOTATION);
        rec.assigned = bool(r, NativeIdentityEntitlementFields.ASSIGNED);
        rec.grantedByRole = bool(r, NativeIdentityEntitlementFields.GRANTED_BY_ROLE);
        rec.allowed = bool(r, NativeIdentityEntitlementFields.ALLOWED);
        rec.connected = bool(r, NativeIdentityEntitlementFields.CONNECTED);
        rec.aggregationState = text(r, NativeIdentityEntitlementFields.AGGREGATION_STATE);
        rec.source = text(r, NativeIdentityEntitlementFields.SOURCE);
        rec.sourceObject = text(r, NativeIdentityEntitlementFields.SOURCE_OBJECT);
        rec.assigner = text(r, NativeIdentityEntitlementFields.ASSIGNER);
        rec.assignmentId = text(r, NativeIdentityEntitlementFields.ASSIGNMENT_ID);
        rec.assignmentNote = text(r, NativeIdentityEntitlementFields.ASSIGNMENT_NOTE);
        rec.sourceAssignableRoles = text(r, NativeIdentityEntitlementFields.SOURCE_ASSIGNABLE_ROLES);
        rec.sourceDetectedRoles = text(r, NativeIdentityEntitlementFields.SOURCE_DETECTED_ROLES);
        rec.certificationItemId = text(r, NativeIdentityEntitlementFields.CERTIFICATION_ITEM_ID);
        rec.pendingCertificationItemId = text(r, NativeIdentityEntitlementFields.PENDING_CERTIFICATION_ITEM_ID);
        rec.requestItemId = text(r, NativeIdentityEntitlementFields.REQUEST_ITEM_ID);
        rec.pendingRequestItemId = text(r, NativeIdentityEntitlementFields.PENDING_REQUEST_ITEM_ID);
        rec.startDate = instant(r, NativeIdentityEntitlementFields.START_DATE);
        rec.endDate = instant(r, NativeIdentityEntitlementFields.END_DATE);
        rec.created = instant(r, NativeIdentityEntitlementFields.CREATED);
        rec.modified = instant(r, NativeIdentityEntitlementFields.MODIFIED);

        rec.srcSystem = text(r, NativeIdentityEntitlementFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeIdentityEntitlementFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeIdentityEntitlementFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeIdentityEntitlementFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeIdentityEntitlementFields.EXTRACTED_AT);
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
