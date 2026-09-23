package com.keyforge.nativeload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One native ProvisioningTransaction row (with its derived items) for upsert into
 * {@code iiq_native.kf_provisioning_txn} + {@code iiq_native.kf_provisioning_item}. Pure data holder.
 */
public final class NativeProvisioningTxnRecord {

    String sourceId;
    String name;
    String operation;
    String type;
    String status;
    String source;
    String integration;
    Boolean forced;
    String identityName;
    String identityDisplayName;
    String applicationName;
    String nativeIdentity;
    String accountDisplayName;
    String certificationId;
    String certificationName;
    String accessRequestId;
    String waitWorkItemId;
    String manualWorkItemId;
    String ticketId;
    String retryRequestId;
    Instant lastRetry;
    Integer retryCount;
    Boolean timedOut;
    Boolean filtered;
    String planResultStatus;
    String planResultRequestId;
    String planResultErrorsJson;
    String accountRequestOperation;
    String requestId;
    Integer itemCount;
    String ownerId;
    String ownerName;
    Instant created;
    Instant modified;

    final List<NativeProvisioningItemRecord> items = new ArrayList<NativeProvisioningItemRecord>();

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
