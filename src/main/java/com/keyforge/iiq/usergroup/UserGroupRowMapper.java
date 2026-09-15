package com.keyforge.iiq.usergroup;

import com.keyforge.iiq.model.UserGroup;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps a source {@link UserGroup} to a {@link UserGroupRow}. Pure and DB-free.
 *
 * <p>The {@code id} is deterministic (from source type + source id/name) so re-runs
 * update in place rather than duplicating. Only source-provided fields are set. Every
 * grid field IdentityIQ returns (id/name/description/modified) has a dedicated column,
 * so {@code customattributes} keeps ONLY fields that have no column and is left NULL
 * when there is nothing unmapped to preserve. No value is derived from users,
 * entitlements, roles, capabilities or any unrelated data.
 */
public final class UserGroupRowMapper {

    private UserGroupRowMapper() {
    }

    public static UserGroupRow map(UserGroup group) {
        String nativeKey = firstNonBlank(group.getId(), group.getName());
        if (nativeKey == null) {
            throw new UserGroupMappingException(
                    "User group has neither an id nor a name; cannot form a deterministic identity.");
        }

        String id = deterministicId(group.getType(), nativeKey);
        String owner = ownerText(group.getOwner());
        Integer memberCount = group.isMembersProvided() ? group.getMembers().size() : null;

        UserGroup.Meta meta = group.getMeta();
        LocalDateTime created = meta == null ? null : parseTimestamp(meta.getCreated());
        LocalDateTime modified = meta == null ? null : parseTimestamp(meta.getLastModified());

        return new UserGroupRow(
                id,
                group.getType(),
                blankToNull(group.getId()),
                blankToNull(group.getName()),
                blankToNull(group.getDescription()),
                owner,
                blankToNull(group.getStatus()),
                created,
                modified,
                memberCount);
    }

    /** Deterministic id from the group's logical identity (type + native key). */
    public static String deterministicId(String type, String nativeKey) {
        String key = "UserGroup|" + nullToEmpty(type) + "|" + nativeKey.trim();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String ownerText(UserGroup.Ref owner) {
        return owner == null ? null : firstNonBlank(owner.getDisplay(), owner.getValue());
    }

    /** IdentityIQ Group Configuration grid date, e.g. {@code "6/29/26, 3:57 AM"} (US locale). */
    private static final DateTimeFormatter IIQ_GRID_DATE =
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US);

    /**
     * Parses a timestamp to UTC, tolerant of the forms IdentityIQ actually returns:
     * ISO-8601 (with or without offset), epoch millis (some ExtJS grids), and the
     * Group Configuration grid's short "M/d/yy, h:mm a" string. Unparseable → null
     * (the raw value is still preserved verbatim in customattributes).
     */
    static LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.matches("\\d{10,}")) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(v)).atZone(ZoneOffset.UTC).toLocalDateTime();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return OffsetDateTime.parse(v).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException withOffset) {
            // fall through
        }
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException withoutOffset) {
            // fall through
        }
        try {
            return LocalDateTime.parse(v, IIQ_GRID_DATE);
        } catch (DateTimeParseException notGridDate) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
