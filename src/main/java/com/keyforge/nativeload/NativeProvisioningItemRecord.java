package com.keyforge.nativeload;

/** One derived native provisioning item (AttributeRequest/PermissionRequest) for kf_provisioning_item. */
public final class NativeProvisioningItemRecord {

    String txnSourceId;
    String identityName;
    String itemType;
    String operation;
    String applicationName;
    String nativeIdentity;
    String instance;
    String accountOperation;
    String name;
    String value;
    String valueJson;
    String assignmentId;
    Boolean assignment;
    String permissionTarget;
    String permissionRights;
    String requestId;
    Integer itemIndex;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;

    public String getTxnSourceId() {
        return txnSourceId;
    }

    public String getName() {
        return name;
    }
}
