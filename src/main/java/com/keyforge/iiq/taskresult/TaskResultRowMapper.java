package com.keyforge.iiq.taskresult;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Maps a {@link TaskResult} to a {@link TaskResultRow}. Pure and DB-free. {@code taskresultid} is the
 * canonicalised IIQ id (raw id also preserved); {@code launched}/{@code completed} are parsed from the
 * SCIM ISO-8601 instant to UTC; all other fields pass through (blanked to NULL only when genuinely
 * empty). Nothing is invented.
 */
public final class TaskResultRowMapper {

    private TaskResultRowMapper() {
    }

    public static TaskResultRow map(TaskResult t) {
        return new TaskResultRow(
                toCanonicalUuid(t.id()),
                blankToNull(t.id()),
                blankToNull(t.name()),
                blankToNull(t.type()),
                blankToNull(t.taskDefinition()),
                blankToNull(t.completionStatus()),
                blankToNull(t.host()),
                blankToNull(t.launcher()),
                parseScimTimestamp(t.launched()),
                parseScimTimestamp(t.completed()),
                t.partitioned(),
                t.terminated(),
                t.pendingSignoffs(),
                blankToNull(t.messagesJson()));
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new TaskResultMappingException(
                    "IdentityIQ TaskResult id is missing; cannot form kf_task_result.taskresultid (UUID).");
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
            throw new TaskResultMappingException(
                    "IdentityIQ TaskResult id '" + rawId + "' is not a valid PostgreSQL UUID.");
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
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
