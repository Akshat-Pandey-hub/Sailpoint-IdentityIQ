package com.keyforge.iiq.accessrequest;

import java.util.List;

/**
 * A SailPoint IdentityIQ Access Request (IdentityRequest), as returned by the modern-UI
 * endpoint {@code GET ui/rest/identityRequests} (verified live). One object carries the
 * request-level fields plus its {@link Item}s (IdentityRequestItems) and {@link Approval}
 * interactions (approval history, incl. the authoritative WorkItem/WorkItemArchive linkage).
 */
public record AccessRequest(
        String id,                    // IdentityRequest GUID
        String requestId,             // human request number, e.g. "0000000021"
        String type,                  // Lifecycle / AccessRequest / ...
        String requesterDisplayName,
        String targetDisplayName,
        String state,
        String executionStatus,
        String completionStatus,
        String priority,
        String externalTicketId,
        Boolean cancelable,
        Long createdDate,             // epoch millis
        Long endDate,
        Long terminatedDate,
        Long verificationDate,
        List<Item> items,
        List<Approval> interactions) {

    /** One requested item (IdentityRequestItem) — a per-item operation on a role/entitlement/account. */
    public record Item(
            String id,
            String operation,          // Add / Remove / Set
            String applicationName,
            String accountName,
            String displayableAccountName,
            String instance,
            String name,               // the attribute, e.g. "assignedRoles"
            String value,
            String displayableValue,
            Boolean role,
            Boolean entitlement,
            Boolean hasManagedAttribute,
            String approvalState,
            String provisioningState,
            String provisioningEngine,
            String provisioningRequestId,
            String assignmentId,
            Integer retries,
            String requesterComments,
            Long startDate,
            Long endDate) {
    }

    /** One approval interaction on the request (approval history / work item). */
    public record Approval(
            String workItemId,          // set while the approval work item is open
            String workItemName,
            String workItemArchiveId,   // set once the approval is completed/archived
            String ownerDisplayName,    // the approver
            String comments,
            String status,
            String description,
            Integer approvalItemCount,
            Long openDate,
            Long completeDate) {
    }
}
