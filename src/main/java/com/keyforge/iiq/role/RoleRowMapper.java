package com.keyforge.iiq.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.keyforge.iiq.model.Role;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Maps an IdentityIQ {@link Role} to a {@link RoleRow}. Pure and DB-free. Every source
 * field goes to its own typed column; {@code descriptions}/{@code classifications} are
 * preserved verbatim as JSON arrays. {@code owner_id} is the canonical owner id (stored
 * as-is, not FK-validated — same convention as {@code application.owner_id}). Nothing is
 * invented; when the source omits a value the column is NULL.
 */
public final class RoleRowMapper {

    private RoleRowMapper() {
    }

    public static RoleRow map(Role role) {
        String roleid = toCanonicalUuid(role.getId());

        Role.Type type = role.getType();
        String roleType = type == null ? null : blankToNull(type.getName());
        String roleTypeDisplay = type == null ? null : blankToNull(type.getDisplayName());

        Role.Ref owner = role.getOwner();
        String ownerId = owner == null ? null : canonicalOrNull(owner.getValue());
        String ownerDisplayName = owner == null ? null : blankToNull(owner.getDisplayName());

        Role.Meta meta = role.getMeta();
        LocalDateTime created = meta == null ? null : parseScimTimestamp(meta.getCreated());
        LocalDateTime lastModified = meta == null ? null : parseScimTimestamp(meta.getLastModified());

        return new RoleRow(
                roleid,
                blankToNull(role.getName()),
                blankToNull(role.getDisplayableName()),
                roleType,
                roleTypeDisplay,
                role.getActive(),
                ownerId,
                ownerDisplayName,
                jsonArrayOrNull(role.getDescriptions()),
                jsonArrayOrNull(role.getClassifications()),
                parseScimTimestamp(role.getActivationDate()),
                parseScimTimestamp(role.getDeactivationDate()),
                created,
                lastModified);
    }

    private static String jsonArrayOrNull(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty() ? node.toString() : null;
    }

    /** Canonical UUID or null (never throws) — for reference ids that may be absent/foreign. */
    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (RoleMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new RoleMappingException(
                    "IdentityIQ role id is missing; cannot use as kf_role.roleid (UUID).");
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
            throw new RoleMappingException(
                    "IdentityIQ role id '" + rawId + "' is not a valid PostgreSQL UUID.");
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
