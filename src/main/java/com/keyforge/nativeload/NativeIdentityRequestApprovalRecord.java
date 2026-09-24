package com.keyforge.nativeload;

import java.time.Instant;

/** One nested native IdentityRequest approval summary for {@code iiq_native.kf_identity_request_approval}. */
public final class NativeIdentityRequestApprovalRecord {

    String requestSourceId;
    String requestName;
    String workItemId;
    String workItemType;
    String owner;
    String ownerId;
    String completer;
    Boolean approved;
    String state;
    String stateKey;
    String typeKey;
    Instant startDate;
    Instant endDate;
    Integer approvalItemCount;
    Integer approvalIndex;
    String commentsJson;
    String signOffJson;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;

    public String getRequestSourceId() {
        return requestSourceId;
    }

    public String getWorkItemId() {
        return workItemId;
    }
}
