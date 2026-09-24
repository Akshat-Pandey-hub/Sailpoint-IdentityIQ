package com.keyforge.nativeload;

import java.time.Instant;

/** One nested native IdentityRequestItem for {@code iiq_native.kf_identity_request_item}. Pure data holder. */
public final class NativeIdentityRequestItemRecord {

    String sourceId;
    String requestSourceId;
    String requestName;
    String application;
    String attributeName;
    String attributeValue;
    String operation;
    String managedAttributeType;
    String assignmentId;
    String nativeIdentity;
    String instance;
    String approverName;
    String approvalState;
    Boolean approved;
    Boolean approvalComplete;
    Boolean rejected;
    String provisioningState;
    String provisioningEngine;
    String provisioningRequestId;
    Boolean provisioningComplete;
    Boolean provisioningFailed;
    String compilationStatus;
    String ownerName;
    String requesterComments;
    Boolean expansion;
    String expansionCause;
    String expansionInfo;
    Integer retries;
    Boolean iiq;
    Instant startDate;
    Instant endDate;
    Instant created;
    Instant modified;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;

    public String getSourceId() {
        return sourceId;
    }

    public String getRequestSourceId() {
        return requestSourceId;
    }
}
