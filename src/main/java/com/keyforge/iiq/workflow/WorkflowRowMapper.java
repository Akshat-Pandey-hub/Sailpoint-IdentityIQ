package com.keyforge.iiq.workflow;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Maps a {@link WorkflowDefinition} to a {@link WorkflowRow}. Pure and DB-free. Approval-config
 * columns are set NULL (not exposed by SCIM); nothing is invented.
 */
public final class WorkflowRowMapper {

    private WorkflowRowMapper() {
    }

    public static WorkflowRow map(WorkflowDefinition wf) {
        String workflowid = toCanonicalUuid(wf.id());
        return new WorkflowRow(
                workflowid,
                blankToNull(wf.name()),
                blankToNull(wf.type()),
                blankToNull(wf.handler()),
                blankToNull(wf.description()),
                null, null, null, null, null,   // approval scheme/mode/levels/escalation/esignature — not in SCIM
                parseScimTimestamp(wf.created()),
                parseScimTimestamp(wf.lastModified()));
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new WorkflowMappingException(
                    "IdentityIQ workflow id is missing; cannot use as kf_workflow_definition.workflowid (UUID).");
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
            throw new WorkflowMappingException(
                    "IdentityIQ workflow id '" + rawId + "' is not a valid PostgreSQL UUID.");
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
