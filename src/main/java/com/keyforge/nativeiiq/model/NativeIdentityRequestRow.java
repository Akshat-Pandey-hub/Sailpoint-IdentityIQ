package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Native-source projection of a SailPoint {@code IdentityRequest} with its derived items and request-time
 * approval summaries. Current-state (mutable until completion). Pure data holder. Nothing inferred.
 */
public final class NativeIdentityRequestRow {

    private String sourceId;          // getId()
    private String name;              // request number/name
    private String type;
    private String userFriendlyType;
    private String state;
    private String source;
    private String sourceObject;      // Source enum name
    private String completionStatus;  // CompletionStatus enum name
    private String executionStatus;   // ExecutionStatus enum name
    private String priority;          // WorkItem.Level name
    private String requesterId;
    private String requesterDisplayName;
    private String targetId;
    private String targetDisplayName;
    private String externalTicketId;
    private String processId;
    private String taskResultId;

    private Boolean executing;
    private Boolean failure;
    private Boolean rejected;
    private Boolean successful;
    private Boolean terminated;
    private Boolean incomplete;
    private Boolean iiqOnly;
    private Boolean provisioningComplete;

    private Instant endDate;
    private Instant verified;
    private Instant created;
    private Instant modified;

    private String ownerId;
    private String ownerName;
    private final List<String> errors = new ArrayList<String>();

    private final List<NativeIdentityRequestItemRow> items = new ArrayList<NativeIdentityRequestItemRow>();
    private final List<NativeIdentityRequestApprovalRow> approvals = new ArrayList<NativeIdentityRequestApprovalRow>();

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.IdentityRequest";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getUserFriendlyType() { return userFriendlyType; }
    public void setUserFriendlyType(String v) { this.userFriendlyType = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getSourceObject() { return sourceObject; }
    public void setSourceObject(String v) { this.sourceObject = v; }
    public String getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(String v) { this.completionStatus = v; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String v) { this.executionStatus = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String v) { this.requesterId = v; }
    public String getRequesterDisplayName() { return requesterDisplayName; }
    public void setRequesterDisplayName(String v) { this.requesterDisplayName = v; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; }
    public String getTargetDisplayName() { return targetDisplayName; }
    public void setTargetDisplayName(String v) { this.targetDisplayName = v; }
    public String getExternalTicketId() { return externalTicketId; }
    public void setExternalTicketId(String v) { this.externalTicketId = v; }
    public String getProcessId() { return processId; }
    public void setProcessId(String v) { this.processId = v; }
    public String getTaskResultId() { return taskResultId; }
    public void setTaskResultId(String v) { this.taskResultId = v; }

    public Boolean getExecuting() { return executing; }
    public void setExecuting(Boolean v) { this.executing = v; }
    public Boolean getFailure() { return failure; }
    public void setFailure(Boolean v) { this.failure = v; }
    public Boolean getRejected() { return rejected; }
    public void setRejected(Boolean v) { this.rejected = v; }
    public Boolean getSuccessful() { return successful; }
    public void setSuccessful(Boolean v) { this.successful = v; }
    public Boolean getTerminated() { return terminated; }
    public void setTerminated(Boolean v) { this.terminated = v; }
    public Boolean getIncomplete() { return incomplete; }
    public void setIncomplete(Boolean v) { this.incomplete = v; }
    public Boolean getIiqOnly() { return iiqOnly; }
    public void setIiqOnly(Boolean v) { this.iiqOnly = v; }
    public Boolean getProvisioningComplete() { return provisioningComplete; }
    public void setProvisioningComplete(Boolean v) { this.provisioningComplete = v; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant v) { this.endDate = v; }
    public Instant getVerified() { return verified; }
    public void setVerified(Instant v) { this.verified = v; }
    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }
    public List<String> getErrors() { return errors; }
    public List<NativeIdentityRequestItemRow> getItems() { return items; }
    public List<NativeIdentityRequestApprovalRow> getApprovals() { return approvals; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }
    public String getSrcInterface() { return srcInterface; }
    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }
    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }
    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
