package com.keyforge.iiq.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.keyforge.iiq.model.Identity;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Maps an IdentityIQ SCIM {@link Identity} to a {@link UserRow}. Pure and DB-free.
 *
 * <p>Every meaningful SCIM field is placed in its own typed column. The complete
 * {@code emails} array and the SailPoint-extension arrays ({@code capabilities},
 * {@code accounts}) are preserved as JSON text — never reduced to a single value.
 * Nothing is invented; a field IdentityIQ does not provide is left null.
 */
public final class UserRowMapper {

    /** IIQ SCIM User extension URN carrying identity attributes. */
    static final String SAILPOINT_USER_EXTENSION = "urn:ietf:params:scim:schemas:sailpoint:1.0:User";
    /** Standard SCIM enterprise-user extension URN. */
    static final String ENTERPRISE_USER_EXTENSION = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private UserRowMapper() {
    }

    public static UserRow map(Identity identity) {
        String userid = toCanonicalUuid(identity.getId());
        JsonNode raw = identity.getExtendedAttributes();
        JsonNode ext = raw.path(SAILPOINT_USER_EXTENSION);

        return new UserRow(
                userid,
                identity.getUserName(),
                identity.getDisplayName(),
                text(raw.path("name"), "formatted"),
                identity.getFirstName(),
                identity.getLastName(),
                blankToNull(identity.getEmail()),          // primary address (scalar convenience)
                jsonArrayOrNull(raw.get("emails")),        // COMPLETE emails array, lossless
                identity.getActive(),
                text(ext, "department"),
                text(ext, "employeeId"),
                bool(ext, "isManager"),
                integer(ext, "riskScore"),
                parseScimTimestamp(text(ext, "lastRefresh")),
                jsonArrayOrNull(ext.get("capabilities")),
                jsonArrayOrNull(ext.get("accounts")),
                jsonObjectOrNull(raw.get(ENTERPRISE_USER_EXTENSION)),
                parseScimTimestamp(text(raw.path("meta"), "created")),
                parseScimTimestamp(text(raw.path("meta"), "lastModified")));
    }

    // --- field readers ------------------------------------------------------

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isBoolean() ? v.asBoolean() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asInt() : null;
    }

    /** JSON text of a non-empty array node, or null (never reduces the array). */
    private static String jsonArrayOrNull(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.toString() : null;
    }

    /** JSON text of a non-empty object node, or null. */
    private static String jsonObjectOrNull(JsonNode node) {
        return node != null && node.isObject() && !node.isEmpty() ? node.toString() : null;
    }

    // --- shared helpers -----------------------------------------------------

    /**
     * Validates the IdentityIQ id as a PostgreSQL UUID. Accepts a 32-hex string
     * (dashes inserted) or an already-dashed UUID; never truncates or alters the value.
     *
     * @throws UserMappingException if the id is not a valid UUID
     */
    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new UserMappingException("IdentityIQ user id is missing; cannot use as usr.userid (UUID).");
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
            throw new UserMappingException(
                    "IdentityIQ user id '" + rawId + "' is not a valid PostgreSQL UUID; "
                    + "record skipped (value not altered).");
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
