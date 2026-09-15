package com.keyforge.iiq.workitem;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code workitem} migration table. Every Work Item field is
 * a plain column and each reference (owner/requester/assignee/target) is flattened to
 * its id + name + display name — no JSON, no arrays, no dumping ground.
 */
public record WorkItemRow(
        String id,
        String workItemName,
        String workItemType,
        String workItemState,
        String accessRequestName,
        String description,
        String priority,
        Integer commentCount,
        Boolean editable,
        Integer reminders,
        Integer escalationCount,
        String completionComments,
        String esigMeaning,
        String certificationId,
        Boolean disableForwarding,
        Boolean forceClassicApprovalUi,
        Boolean newTypeWorkItem,
        String ownerId,
        String ownerName,
        String ownerDisplayName,
        String requesterId,
        String requesterName,
        String requesterDisplayName,
        String assigneeId,
        String assigneeName,
        String assigneeDisplayName,
        String targetId,
        String targetName,
        String targetDisplayName,
        LocalDateTime createdAt,
        LocalDateTime notificationDate,
        LocalDateTime expirationDate,
        LocalDateTime wakeUpDate) {
}
