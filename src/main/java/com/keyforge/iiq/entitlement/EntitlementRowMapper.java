package com.keyforge.iiq.entitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.keyforge.iiq.model.Entitlement;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

/**
 * Maps an IdentityIQ {@link Entitlement} to an {@link EntitlementRow}. Pure and DB-free.
 * Every source field goes to its own typed column ({@code requestable} is not lost);
 * {@code schemas} is preserved as a JSON array. The application relationship becomes a
 * resolved {@code instanceid} FK. Nothing is invented.
 */
public final class EntitlementRowMapper {

    private EntitlementRowMapper() {
    }

    public static EntitlementRow map(Entitlement entitlement, Set<String> existingInstanceIds) {
        String entitlementid = toCanonicalUuid(entitlement.getId());
        String instanceid = resolveInstanceId(entitlement.getApplication(), existingInstanceIds);
        String applicationDisplayName = entitlement.getApplication() == null
                ? null : blankToNull(entitlement.getApplication().getDisplayName());

        JsonNode extra = entitlement.getAdditionalAttributes();
        Boolean aggregated = extra.path("aggregated").isBoolean() ? extra.path("aggregated").asBoolean() : null;
        LocalDateTime lastRefresh = parseScimTimestamp(text(extra, "lastRefresh"));

        Entitlement.Meta meta = entitlement.getMeta();
        LocalDateTime created = meta == null ? null : parseScimTimestamp(meta.getCreated());
        LocalDateTime lastModified = meta == null ? null : parseScimTimestamp(meta.getLastModified());

        return new EntitlementRow(
                entitlementid,
                blankToNull(entitlement.getValue()),
                blankToNull(entitlement.getDisplayableName()),
                blankToNull(entitlement.getType()),            // raw source string (text column)
                blankToNull(entitlement.getAttribute()),
                aggregated,
                entitlement.getRequestable(),
                instanceid,
                applicationDisplayName,
                lastRefresh,
                created,
                lastModified,
                jsonArrayOrNull(extra.get("schemas")));
    }

    public static String resolveInstanceId(Entitlement.ApplicationRef application, Set<String> existingInstanceIds) {
        if (application == null || application.getValue() == null || application.getValue().isBlank()) {
            return null;
        }
        String canonical;
        try {
            canonical = toCanonicalUuid(application.getValue());
        } catch (EntitlementMappingException notAUuid) {
            return null;
        }
        return existingInstanceIds != null && existingInstanceIds.contains(canonical) ? canonical : null;
    }

    public static boolean hasApplicationRef(Entitlement entitlement) {
        Entitlement.ApplicationRef app = entitlement.getApplication();
        return app != null && app.getValue() != null && !app.getValue().isBlank();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String jsonArrayOrNull(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.toString() : null;
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new EntitlementMappingException(
                    "IdentityIQ entitlement id is missing; cannot use as entitlement.entitlementid (UUID).");
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return UUID.fromString(dashed).toString();
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            throw new EntitlementMappingException(
                    "IdentityIQ entitlement id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    static LocalDateTime parseScimTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException withOffset) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (DateTimeParseException withoutOffset) {
                return null;
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
