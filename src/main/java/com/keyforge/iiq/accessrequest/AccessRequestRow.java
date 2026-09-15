package com.keyforge.iiq.accessrequest;

import java.time.LocalDateTime;

/** A row of {@code kf_access_request} (IdentityRequest, request level). */
public record AccessRequestRow(
        String requestid,
        String requestNumber,
        String type,
        String requesterDisplayName,
        String targetDisplayName,
        String state,
        String executionStatus,
        String completionStatus,
        String priority,
        String externalTicketId,
        Boolean cancelable,
        LocalDateTime createdAt,
        LocalDateTime endDate,
        LocalDateTime terminatedDate,
        LocalDateTime verificationDate,
        Integer itemCount) {
}
