package com.keyforge.iiq.workitem;

import com.keyforge.iiq.model.WorkItem;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Maps an IdentityIQ {@link WorkItem} to a {@link WorkItemRow}. Pure and DB-free.
 * Scalars go straight to columns; references are flattened to id/name/displayName;
 * epoch-millis timestamps become {@link LocalDateTime} (UTC). Nothing is invented.
 */
public final class WorkItemRowMapper {

    private WorkItemRowMapper() {
    }

    public static WorkItemRow map(WorkItem w) {
        String id = toCanonicalUuid(w.getId());

        WorkItem.Ref owner = w.getOwner();
        WorkItem.Ref requester = w.getRequester();
        WorkItem.Ref assignee = w.getAssignee();
        WorkItem.Ref target = w.getTarget();

        return new WorkItemRow(
                id,
                w.getWorkItemName(),
                w.getWorkItemType(),
                w.getWorkItemState(),
                w.getAccessRequestName(),
                w.getDescription(),
                w.getPriority(),
                w.getCommentCount(),
                w.getEditable(),
                w.getReminders(),
                w.getEscalationCount(),
                w.getCompletionComments(),
                w.getEsigMeaning(),
                w.getCertificationId(),           // opaque reference id, stored as text
                w.getDisableForwarding(),
                w.getForceClassicApprovalUI(),
                w.getNewTypeWorkItem(),
                owner == null ? null : canonicalOrNull(owner.getId()),
                owner == null ? null : owner.getName(),
                owner == null ? null : owner.getDisplayName(),
                requester == null ? null : canonicalOrNull(requester.getId()),
                requester == null ? null : requester.getName(),
                requester == null ? null : requester.getDisplayName(),
                assignee == null ? null : canonicalOrNull(assignee.getId()),
                assignee == null ? null : assignee.getName(),
                assignee == null ? null : assignee.getDisplayName(),
                target == null ? null : canonicalOrNull(target.getId()),
                target == null ? null : target.getName(),
                target == null ? null : target.getDisplayName(),
                epochToUtc(w.getCreated()),
                epochToUtc(w.getNotificationDate()),
                epochToUtc(w.getExpirationDate()),
                epochToUtc(w.getWakeUpDate()));
    }

    /** Epoch millis → UTC {@link LocalDateTime}, or null. */
    static LocalDateTime epochToUtc(Long epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Canonical UUID or null; never throws (for reference ids that may be odd/absent). */
    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (WorkItemMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new WorkItemMappingException(
                    "IdentityIQ work item id is missing; cannot use as workitem.id (UUID).");
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
            throw new WorkItemMappingException(
                    "IdentityIQ work item id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }
}
