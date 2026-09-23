package com.keyforge.nativeiiq.model;

/**
 * Native-source projection of one provisioning item — an {@code AttributeRequest} or {@code PermissionRequest}
 * derived from a {@code ProvisioningTransaction}'s embedded {@code AccountRequest} (the {@code "request"}
 * attribute). Provisioning items are NOT independently persisted IIQ objects; they are owned by the
 * transaction's request, so they are derived here (deterministic id from the parent txn + item content).
 * Pure data holder. Secret values are redacted by the mapper before this row is built.
 */
public final class NativeProvisioningItemRow {

    private String txnSourceId;     // parent ProvisioningTransaction getId()
    private String identityName;    // carried from the parent txn for convenience
    private String itemType;        // ATTRIBUTE | PERMISSION
    private String operation;       // GenericRequest.getOp() name
    private String applicationName; // AccountRequest.getApplicationName()
    private String nativeIdentity;  // AccountRequest.getNativeIdentity()
    private String instance;        // AccountRequest.getInstance()
    private String accountOperation;// AccountRequest.getOperation() name
    private String name;            // AttributeRequest.getName() / PermissionRequest target
    private String value;           // AttributeRequest value (redacted if secret) / PermissionRequest rights
    private Object valueJson;       // JSON-safe scalar/list/map value; redacted before normalization
    private String assignmentId;
    private Boolean assignment;
    private String permissionTarget;
    private String permissionRights;
    private String requestId;       // AccountRequest.getRequestID()
    private int itemIndex;          // disambiguator for duplicate (name,value)

    public String getTxnSourceId() { return txnSourceId; }
    public void setTxnSourceId(String v) { this.txnSourceId = v; }
    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }
    public String getItemType() { return itemType; }
    public void setItemType(String v) { this.itemType = v; }
    public String getOperation() { return operation; }
    public void setOperation(String v) { this.operation = v; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String v) { this.applicationName = v; }
    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }
    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }
    public String getAccountOperation() { return accountOperation; }
    public void setAccountOperation(String v) { this.accountOperation = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getValue() { return value; }
    public void setValue(String v) { this.value = v; }
    public Object getValueJson() { return valueJson; }
    public void setValueJson(Object v) { this.valueJson = v; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String v) { this.assignmentId = v; }
    public Boolean getAssignment() { return assignment; }
    public void setAssignment(Boolean v) { this.assignment = v; }
    public String getPermissionTarget() { return permissionTarget; }
    public void setPermissionTarget(String v) { this.permissionTarget = v; }
    public String getPermissionRights() { return permissionRights; }
    public void setPermissionRights(String v) { this.permissionRights = v; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String v) { this.requestId = v; }
    public int getItemIndex() { return itemIndex; }
    public void setItemIndex(int v) { this.itemIndex = v; }
}
