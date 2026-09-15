package com.keyforge.iiq.application;

import com.keyforge.iiq.model.Application;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

/**
 * Maps an IdentityIQ {@link Application} to an {@link ApplicationInstanceRow}. Pure and
 * DB-free. FK columns ({@code applicationid}, {@code ownerId}) are resolved against ids
 * that actually exist in the migration tables, so no reference is ever invented.
 */
public final class ApplicationInstanceRowMapper {

    private ApplicationInstanceRowMapper() {
    }

    public static ApplicationInstanceRow map(Application application,
                                             Set<String> existingApplicationIds,
                                             Set<String> existingUserIds) {
        String instanceid = toCanonicalUuid(application.getId());

        String applicationid = existingApplicationIds != null && existingApplicationIds.contains(instanceid)
                ? instanceid : null;

        String ownerId = resolveOwnerId(application.getOwner(), existingUserIds);
        String ownerDisplayName = application.getOwner() == null
                ? null : blankToNull(application.getOwner().getDisplayName());

        Application.Meta meta = application.getMeta();
        LocalDateTime created = meta == null ? null : parseScimTimestamp(meta.getCreated());
        LocalDateTime lastModified = meta == null ? null : parseScimTimestamp(meta.getLastModified());

        return new ApplicationInstanceRow(
                instanceid,
                applicationid,
                blankToNull(application.getName()),
                blankToNull(application.getType()),
                ownerId,
                ownerDisplayName,
                created,
                lastModified);
    }

    /** Resolves the owner reference to a userid that exists in {@code usr}, or null. */
    public static String resolveOwnerId(Application.Owner owner, Set<String> existingUserIds) {
        if (owner == null || owner.getValue() == null || owner.getValue().isBlank()) {
            return null;
        }
        String canonical;
        try {
            canonical = toCanonicalUuid(owner.getValue());
        } catch (ApplicationInstanceMappingException notAUuid) {
            return null;
        }
        return existingUserIds != null && existingUserIds.contains(canonical) ? canonical : null;
    }

    public static boolean hasOwnerReference(Application application) {
        Application.Owner owner = application.getOwner();
        return owner != null && owner.getValue() != null && !owner.getValue().isBlank();
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ApplicationInstanceMappingException(
                    "IdentityIQ application id is missing; cannot use as applicationinstance.instanceid (UUID).");
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
            throw new ApplicationInstanceMappingException(
                    "IdentityIQ application id '" + rawId + "' is not a valid PostgreSQL UUID.");
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
