package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * Native-source projection of a SailPoint {@code IdentityRequestItem} (a line of an IdentityRequest).
 * Derived from the parent request's {@code getItems()}; each item is itself a persisted object with its own
 * id. Carries the item-level approval + provisioning state and the {@code expansionCause} (direct/role/
 * birthright/inherited provenance). Pure data holder. Nothing inferred.
 */
public final class NativeIdentityRequestItemRow {

    private String sourceId;          // IdentityRequestItem.getId()
    private String requestSourceId;   // parent IdentityRequest.getId()
    private String requestName;       // parent request number/name
    private String application;
    private String attributeName;     // getName()
    private String attributeValue;    // getStringValue()
    private String operation;         // add/remove/…
    private String managedAttributeType;
    private String assignmentId;
    private String nativeIdentity;
    private String instance;

    // approval (item-level)
    private String approverName;
    private String approvalState;     // WorkItem.State name
    private Boolean approved;
    private Boolean approvalComplete;
    private Boolean rejected;

    // provisioning (item-level)
    private String provisioningState; // ApprovalItem.ProvisioningState name
    private String provisioningEngine;
    private String provisioningRequestId;
    private Boolean provisioningComplete;
    private Boolean provisioningFailed;
    private String compilationStatus;

    // provenance / ownership
    private String ownerName;
    private String requesterComments;
    private Boolean expansion;
    private String expansionCause;    // ExpansionItem.Cause name — WHY the item is in the request
    private String expansionInfo;
    private Integer retries;
    private Boolean iiq;

    private Instant startDate;
    private Instant endDate;
    private Instant created;
    private Instant modified;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getRequestSourceId() { return requestSourceId; }
    public void setRequestSourceId(String v) { this.requestSourceId = v; }
    public String getRequestName() { return requestName; }
    public void setRequestName(String v) { this.requestName = v; }
    public String getApplication() { return application; }
    public void setApplication(String v) { this.application = v; }
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String v) { this.attributeName = v; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String v) { this.attributeValue = v; }
    public String getOperation() { return operation; }
    public void setOperation(String v) { this.operation = v; }
    public String getManagedAttributeType() { return managedAttributeType; }
    public void setManagedAttributeType(String v) { this.managedAttributeType = v; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String v) { this.assignmentId = v; }
    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }
    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }

    public String getApproverName() { return approverName; }
    public void setApproverName(String v) { this.approverName = v; }
    public String getApprovalState() { return approvalState; }
    public void setApprovalState(String v) { this.approvalState = v; }
    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean v) { this.approved = v; }
    public Boolean getApprovalComplete() { return approvalComplete; }
    public void setApprovalComplete(Boolean v) { this.approvalComplete = v; }
    public Boolean getRejected() { return rejected; }
    public void setRejected(Boolean v) { this.rejected = v; }

    public String getProvisioningState() { return provisioningState; }
    public void setProvisioningState(String v) { this.provisioningState = v; }
    public String getProvisioningEngine() { return provisioningEngine; }
    public void setProvisioningEngine(String v) { this.provisioningEngine = v; }
    public String getProvisioningRequestId() { return provisioningRequestId; }
    public void setProvisioningRequestId(String v) { this.provisioningRequestId = v; }
    public Boolean getProvisioningComplete() { return provisioningComplete; }
    public void setProvisioningComplete(Boolean v) { this.provisioningComplete = v; }
    public Boolean getProvisioningFailed() { return provisioningFailed; }
    public void setProvisioningFailed(Boolean v) { this.provisioningFailed = v; }
    public String getCompilationStatus() { return compilationStatus; }
    public void setCompilationStatus(String v) { this.compilationStatus = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }
    public String getRequesterComments() { return requesterComments; }
    public void setRequesterComments(String v) { this.requesterComments = v; }
    public Boolean getExpansion() { return expansion; }
    public void setExpansion(Boolean v) { this.expansion = v; }
    public String getExpansionCause() { return expansionCause; }
    public void setExpansionCause(String v) { this.expansionCause = v; }
    public String getExpansionInfo() { return expansionInfo; }
    public void setExpansionInfo(String v) { this.expansionInfo = v; }
    public Integer getRetries() { return retries; }
    public void setRetries(Integer v) { this.retries = v; }
    public Boolean getIiq() { return iiq; }
    public void setIiq(Boolean v) { this.iiq = v; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant v) { this.startDate = v; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant v) { this.endDate = v; }
    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }
}
