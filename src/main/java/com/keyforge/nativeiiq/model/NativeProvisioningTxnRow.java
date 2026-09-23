package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Native-source projection of a SailPoint {@code ProvisioningTransaction}. Current-state (mutable until
 * completion). Flat top-level fields plus the link ids read from the {@code getAttributes()} map (never the
 * whole map), the {@code planResult} outcome, and the derived provisioning items from the embedded
 * {@code AccountRequest}. Pure data holder. Nothing inferred.
 */
public final class NativeProvisioningTxnRow {

    private String sourceId;
    private String name;
    private String operation;
    private String type;              // ProvisioningTransaction.Type name
    private String status;            // ProvisioningTransaction.Status name
    private String source;
    private String integration;
    private Boolean forced;

    private String identityName;
    private String identityDisplayName;
    private String applicationName;
    private String nativeIdentity;
    private String accountDisplayName;

    // native references (ids/names only — resolution deferred, NULL when absent)
    private String certificationId;
    private String certificationName;
    private String accessRequestId;
    private String waitWorkItemId;
    private String manualWorkItemId;
    private String ticketId;
    private String retryRequestId;
    private Instant lastRetry;
    private Integer retryCount;
    private Boolean timedOut;
    private Boolean filtered;

    // planResult (ProvisioningResult) + request
    private String planResultStatus;
    private String planResultRequestId;
    private final List<String> planResultErrors = new ArrayList<String>();
    private String accountRequestOperation;
    private String requestId;

    // derived items
    private final List<NativeProvisioningItemRow> items = new ArrayList<NativeProvisioningItemRow>();

    private String ownerId;
    private String ownerName;
    private Instant created;
    private Instant modified;

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.ProvisioningTransaction";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getOperation() { return operation; }
    public void setOperation(String v) { this.operation = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getIntegration() { return integration; }
    public void setIntegration(String v) { this.integration = v; }
    public Boolean getForced() { return forced; }
    public void setForced(Boolean v) { this.forced = v; }

    public String getIdentityName() { return identityName; }
    public void setIdentityName(String v) { this.identityName = v; }
    public String getIdentityDisplayName() { return identityDisplayName; }
    public void setIdentityDisplayName(String v) { this.identityDisplayName = v; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String v) { this.applicationName = v; }
    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }
    public String getAccountDisplayName() { return accountDisplayName; }
    public void setAccountDisplayName(String v) { this.accountDisplayName = v; }

    public String getCertificationId() { return certificationId; }
    public void setCertificationId(String v) { this.certificationId = v; }
    public String getCertificationName() { return certificationName; }
    public void setCertificationName(String v) { this.certificationName = v; }
    public String getAccessRequestId() { return accessRequestId; }
    public void setAccessRequestId(String v) { this.accessRequestId = v; }
    public String getWaitWorkItemId() { return waitWorkItemId; }
    public void setWaitWorkItemId(String v) { this.waitWorkItemId = v; }
    public String getManualWorkItemId() { return manualWorkItemId; }
    public void setManualWorkItemId(String v) { this.manualWorkItemId = v; }
    public String getTicketId() { return ticketId; }
    public void setTicketId(String v) { this.ticketId = v; }
    public String getRetryRequestId() { return retryRequestId; }
    public void setRetryRequestId(String v) { this.retryRequestId = v; }
    public Instant getLastRetry() { return lastRetry; }
    public void setLastRetry(Instant v) { this.lastRetry = v; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer v) { this.retryCount = v; }
    public Boolean getTimedOut() { return timedOut; }
    public void setTimedOut(Boolean v) { this.timedOut = v; }
    public Boolean getFiltered() { return filtered; }
    public void setFiltered(Boolean v) { this.filtered = v; }

    public String getPlanResultStatus() { return planResultStatus; }
    public void setPlanResultStatus(String v) { this.planResultStatus = v; }
    public String getPlanResultRequestId() { return planResultRequestId; }
    public void setPlanResultRequestId(String v) { this.planResultRequestId = v; }
    public List<String> getPlanResultErrors() { return planResultErrors; }
    public String getAccountRequestOperation() { return accountRequestOperation; }
    public void setAccountRequestOperation(String v) { this.accountRequestOperation = v; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String v) { this.requestId = v; }

    public List<NativeProvisioningItemRow> getItems() { return items; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }
    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

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
