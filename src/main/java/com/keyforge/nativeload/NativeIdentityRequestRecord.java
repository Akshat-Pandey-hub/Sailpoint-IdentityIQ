package com.keyforge.nativeload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One native IdentityRequest row (with its nested items + approval summaries) for upsert into
 * {@code iiq_native.kf_identity_request} (+ {@code kf_identity_request_item} /
 * {@code kf_identity_request_approval}). Pure data holder.
 */
public final class NativeIdentityRequestRecord {

    String sourceId;
    String name;
    String type;
    String userFriendlyType;
    String state;
    String source;
    String sourceObject;
    String completionStatus;
    String executionStatus;
    String priority;
    String requesterId;
    String requesterDisplayName;
    String targetId;
    String targetDisplayName;
    String externalTicketId;
    String processId;
    String taskResultId;
    Boolean executing;
    Boolean failure;
    Boolean rejected;
    Boolean successful;
    Boolean terminated;
    Boolean incomplete;
    Boolean iiqOnly;
    Boolean provisioningComplete;
    Instant endDate;
    Instant verified;
    Instant created;
    Instant modified;
    String ownerId;
    String ownerName;
    String errorsJson;
    Integer itemCount;
    Integer approvalCount;

    final List<NativeIdentityRequestItemRecord> items = new ArrayList<NativeIdentityRequestItemRecord>();
    final List<NativeIdentityRequestApprovalRecord> approvals = new ArrayList<NativeIdentityRequestApprovalRecord>();

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }
}
