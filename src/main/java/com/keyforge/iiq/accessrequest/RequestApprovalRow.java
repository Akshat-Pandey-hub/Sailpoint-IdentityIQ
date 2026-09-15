package com.keyforge.iiq.accessrequest;

import java.time.LocalDateTime;

/**
 * A row of {@code kf_request_approval} (approval interaction → its request). The authoritative
 * link to the existing {@code workitem} data is {@code work_item_id} (open approvals) or
 * {@code work_item_archive_id} (completed/archived) — from the request's own interactions,
 * not a name correlation.
 */
public record RequestApprovalRow(
        String id,
        String requestid,
        String requestNumber,
        String ownerDisplayName,
        String status,
        String description,
        String comments,
        Integer approvalItemCount,
        String workItemId,
        String workItemName,
        String workItemArchiveId,
        LocalDateTime openDate,
        LocalDateTime completeDate) {
}
