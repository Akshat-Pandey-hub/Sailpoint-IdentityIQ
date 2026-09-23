package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the plugin endpoint's JSON envelope into {@link NativeApplicationRecord}s. Fails loudly on a
 * non-JSON body, a body carrying a controlled {@code error} object, or a body without a {@code rows}
 * array — an IIQ error can never masquerade as "0 extracted". A genuine {@code {"rows":[]}} returns
 * empty. Nested structures are preserved verbatim as JSON strings; ISO-8601 timestamps become
 * {@link Instant} (a bad timestamp becomes {@code null}).
 */
final class NativeApplicationParser {

    private final ObjectMapper mapper = new ObjectMapper();

    List<NativeApplicationRecord> parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json == null ? "" : json);
        } catch (Exception e) {
            throw new NativeImportException("Native Application endpoint did not return JSON: "
                    + e.getMessage() + " -- body: " + snippet(json), e);
        }
        if (root == null || !root.isObject()) {
            throw new NativeImportException("Unexpected native Application response (not a JSON object) -- body: "
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
        JsonNode rows = root.get(NativeApplicationFields.ROWS);
        if (rows == null || !rows.isArray()) {
            throw new NativeImportException("Unexpected native Application response (no '"
                    + NativeApplicationFields.ROWS + "' array) -- body: " + snippet(json));
        }
        List<NativeApplicationRecord> out = new ArrayList<>();
        for (JsonNode r : rows) {
            out.add(toRecord(r));
        }
        return out;
    }

    private NativeApplicationRecord toRecord(JsonNode r) {
        NativeApplicationRecord rec = new NativeApplicationRecord();
        rec.sourceId = text(r, NativeApplicationFields.SOURCE_ID);
        rec.name = text(r, NativeApplicationFields.NAME);
        rec.description = text(r, NativeApplicationFields.DESCRIPTION);
        rec.type = text(r, NativeApplicationFields.TYPE);
        rec.connector = text(r, NativeApplicationFields.CONNECTOR);
        rec.featuresString = text(r, NativeApplicationFields.FEATURES_STRING);
        rec.profileClass = text(r, NativeApplicationFields.PROFILE_CLASS);
        rec.proxiedName = text(r, NativeApplicationFields.PROXIED_NAME);
        rec.cluster = text(r, NativeApplicationFields.CLUSTER);
        rec.icon = text(r, NativeApplicationFields.ICON);
        rec.aggregationTypes = text(r, NativeApplicationFields.AGGREGATION_TYPES);
        rec.beforeProvisioningRule = text(r, NativeApplicationFields.BEFORE_PROVISIONING_RULE);
        rec.afterProvisioningRule = text(r, NativeApplicationFields.AFTER_PROVISIONING_RULE);
        rec.accountSchemaCorrelationRule = text(r, NativeApplicationFields.ACCOUNT_SCHEMA_CORRELATION_RULE);
        rec.accountSchemaCustomizationRule = text(r, NativeApplicationFields.ACCOUNT_SCHEMA_CUSTOMIZATION_RULE);
        rec.accountSchemaCreationRule = text(r, NativeApplicationFields.ACCOUNT_SCHEMA_CREATION_RULE);
        rec.accountSchemaRefreshRule = text(r, NativeApplicationFields.ACCOUNT_SCHEMA_REFRESH_RULE);
        rec.applicationCreationRule = text(r, NativeApplicationFields.APPLICATION_CREATION_RULE);
        rec.score = intOrNull(r, NativeApplicationFields.SCORE);

        rec.authoritative = bool(r, NativeApplicationFields.AUTHORITATIVE);
        rec.caseInsensitive = bool(r, NativeApplicationFields.CASE_INSENSITIVE);
        rec.logical = bool(r, NativeApplicationFields.LOGICAL);
        rec.composite = bool(r, NativeApplicationFields.COMPOSITE);
        rec.authenticationResource = bool(r, NativeApplicationFields.AUTHENTICATION_RESOURCE);
        rec.activityEnabled = bool(r, NativeApplicationFields.ACTIVITY_ENABLED);
        rec.inMaintenance = bool(r, NativeApplicationFields.IN_MAINTENANCE);
        rec.managesOtherApps = bool(r, NativeApplicationFields.MANAGES_OTHER_APPS);
        rec.nativeChangeDetectionEnabled = bool(r, NativeApplicationFields.NATIVE_CHANGE_DETECTION_ENABLED);
        rec.supportsProvisioning = bool(r, NativeApplicationFields.SUPPORTS_PROVISIONING);
        rec.supportsAccountOnly = bool(r, NativeApplicationFields.SUPPORTS_ACCOUNT_ONLY);
        rec.supportsAdditionalAccounts = bool(r, NativeApplicationFields.SUPPORTS_ADDITIONAL_ACCOUNTS);
        rec.supportsAuthenticate = bool(r, NativeApplicationFields.SUPPORTS_AUTHENTICATE);
        rec.supportsGroupProvisioning = bool(r, NativeApplicationFields.SUPPORTS_GROUP_PROVISIONING);
        rec.supportsDirectPermissions = bool(r, NativeApplicationFields.SUPPORTS_DIRECT_PERMISSIONS);
        rec.syncProvisioning = bool(r, NativeApplicationFields.SYNC_PROVISIONING);

        rec.ownerId = text(r, NativeApplicationFields.OWNER_ID);
        rec.ownerName = text(r, NativeApplicationFields.OWNER_NAME);

        rec.secondaryOwnersJson = json(r, NativeApplicationFields.SECONDARY_OWNERS);
        rec.remediatorsJson = json(r, NativeApplicationFields.REMEDIATORS);
        rec.dependenciesJson = json(r, NativeApplicationFields.DEPENDENCIES);
        rec.schemasJson = json(r, NativeApplicationFields.SCHEMAS);
        rec.descriptionsJson = json(r, NativeApplicationFields.DESCRIPTIONS);
        rec.attributesJson = json(r, NativeApplicationFields.ATTRIBUTES);

        rec.created = instant(r, NativeApplicationFields.CREATED);
        rec.modified = instant(r, NativeApplicationFields.MODIFIED);

        rec.srcSystem = text(r, NativeApplicationFields.SRC_SYSTEM);
        rec.srcInterface = text(r, NativeApplicationFields.SRC_INTERFACE);
        rec.srcObjectType = text(r, NativeApplicationFields.SRC_OBJECT_TYPE);
        rec.extractionRunId = text(r, NativeApplicationFields.EXTRACTION_RUN_ID);
        rec.extractedAt = instant(r, NativeApplicationFields.EXTRACTED_AT);
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

    private static Integer intOrNull(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return (n == null || n.isNull()) ? null : Integer.valueOf(n.asInt());
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
