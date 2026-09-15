package com.keyforge.iiq.accessrequest;

import java.time.LocalDateTime;

/** A row of {@code kf_request_item} (IdentityRequestItem → its request). */
public record RequestItemRow(
        String itemid,
        String requestid,
        String requestNumber,
        String operation,
        String applicationName,
        String accountName,
        String displayableAccountName,
        String instance,
        String name,
        String value,
        String displayableValue,
        Boolean isRole,
        Boolean isEntitlement,
        Boolean hasManagedAttribute,
        String approvalState,
        String provisioningState,
        String provisioningEngine,
        String assignmentId,
        Integer retries,
        String requesterComments,
        LocalDateTime startDate,
        LocalDateTime endDate) {
}
