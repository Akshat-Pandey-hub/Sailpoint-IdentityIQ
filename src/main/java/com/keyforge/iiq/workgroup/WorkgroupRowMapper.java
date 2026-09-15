package com.keyforge.iiq.workgroup;

import com.keyforge.iiq.model.UserGroup;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps a source {@link UserGroup} of type Workgroup to a {@link WorkgroupRow}. Pure and
 * DB-free. The primary key is the real IdentityIQ workgroup id (canonical UUID). Every
 * value comes from an actual {@link UserGroup} field; fields the current DataSource does
 * not provide (owner, status, created, member count) resolve to NULL rather than being
 * invented — and would populate automatically if IdentityIQ later returns them.
 */
public final class WorkgroupRowMapper {

    private WorkgroupRowMapper() {
    }

    public static WorkgroupRow map(UserGroup group) {
        String workgroupid = toCanonicalUuid(group.getId());

        UserGroup.Ref owner = group.getOwner();
        String ownerId = owner == null ? null : canonicalOrNull(owner.getValue());
        String ownerDisplayName = owner == null ? null : blankToNull(owner.getDisplay());

        UserGroup.Meta meta = group.getMeta();
        LocalDateTime created = meta == null ? null : parseTimestamp(meta.getCreated());
        LocalDateTime modified = meta == null ? null : parseTimestamp(meta.getLastModified());

        Integer memberCount = group.isMembersProvided() ? group.getMembers().size() : null;

        return new WorkgroupRow(
                workgroupid,
                blankToNull(group.getId()),
                blankToNull(group.getName()),
                blankToNull(group.getDescription()),
                ownerId,
                ownerDisplayName,
                blankToNull(group.getStatus()),
                created,
                modified,
                memberCount);
    }

    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (WorkgroupMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new WorkgroupMappingException(
                    "IdentityIQ workgroup id is missing; cannot use as kf_workgroup.workgroupid (UUID).");
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
            throw new WorkgroupMappingException(
                    "IdentityIQ workgroup id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    /** IdentityIQ Group Configuration grid date, e.g. {@code "6/28/26, 10:27 PM"} (US locale). */
    private static final DateTimeFormatter IIQ_GRID_DATE =
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US);

    /**
     * Parses a timestamp to UTC, tolerant of the forms IdentityIQ actually returns:
     * ISO-8601 (with/without offset), epoch millis, and the Group Configuration grid's
     * short "M/d/yy, h:mm a" string. Unparseable → null. (Mirrors the tolerant parser
     * already used for the {@code usergroup} table so behaviour is identical.)
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
