package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * Native-source projection of a SailPoint {@code CertificationEntity} — a single certified subject (identity,
 * account group, or data owner) within a certification. Current-state (mutable) data. Pure data holder. The
 * referenced Certification and Identity owner are preserved as reference columns rather than nested objects.
 * The latest {@code CertificationAction} decision is flattened into scalar action_* columns. Nothing inferred.
 */
public final class NativeCertificationEntityRow {

    private String sourceId;
    private String certificationId;
    private String identity;
    private String application;
    private String nativeIdentity;
    private String accountGroup;
    private String firstName;
    private String lastName;
    private String fullName;
    private String referenceAttribute;
    private String schemaObjectType;
    private String snapshotId;
    private String pendingCertification;
    private String type;                    // CertificationEntity.Type name
    private String summaryStatus;           // AbstractCertificationItem.Status name
    private Boolean entityDelegated;
    private String entityDelegationStatus;
    private Integer compositeScore;
    private String targetId;
    private String targetName;
    private String targetDisplayName;

    // dates
    private Instant completed;
    private Instant created;
    private Instant modified;

    private String ownerId;
    private String ownerName;

    // latest action decision (flattened)
    private String actionStatus;
    private Instant actionDecisionDate;
    private String actionRemediationAction;
    private String actionActorName;
    private String actionActorDisplayName;
    private String actionComments;

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.CertificationEntity";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getCertificationId() { return certificationId; }
    public void setCertificationId(String v) { this.certificationId = v; }
    public String getIdentity() { return identity; }
    public void setIdentity(String v) { this.identity = v; }
    public String getApplication() { return application; }
    public void setApplication(String v) { this.application = v; }
    public String getNativeIdentity() { return nativeIdentity; }
    public void setNativeIdentity(String v) { this.nativeIdentity = v; }
    public String getAccountGroup() { return accountGroup; }
    public void setAccountGroup(String v) { this.accountGroup = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getReferenceAttribute() { return referenceAttribute; }
    public void setReferenceAttribute(String v) { this.referenceAttribute = v; }
    public String getSchemaObjectType() { return schemaObjectType; }
    public void setSchemaObjectType(String v) { this.schemaObjectType = v; }
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String v) { this.snapshotId = v; }
    public String getPendingCertification() { return pendingCertification; }
    public void setPendingCertification(String v) { this.pendingCertification = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getSummaryStatus() { return summaryStatus; }
    public void setSummaryStatus(String v) { this.summaryStatus = v; }
    public Boolean getEntityDelegated() { return entityDelegated; }
    public void setEntityDelegated(Boolean v) { this.entityDelegated = v; }
    public String getEntityDelegationStatus() { return entityDelegationStatus; }
    public void setEntityDelegationStatus(String v) { this.entityDelegationStatus = v; }
    public Integer getCompositeScore() { return compositeScore; }
    public void setCompositeScore(Integer v) { this.compositeScore = v; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String v) { this.targetName = v; }
    public String getTargetDisplayName() { return targetDisplayName; }
    public void setTargetDisplayName(String v) { this.targetDisplayName = v; }

    public Instant getCompleted() { return completed; }
    public void setCompleted(Instant v) { this.completed = v; }
    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public String getActionStatus() { return actionStatus; }
    public void setActionStatus(String v) { this.actionStatus = v; }
    public Instant getActionDecisionDate() { return actionDecisionDate; }
    public void setActionDecisionDate(Instant v) { this.actionDecisionDate = v; }
    public String getActionRemediationAction() { return actionRemediationAction; }
    public void setActionRemediationAction(String v) { this.actionRemediationAction = v; }
    public String getActionActorName() { return actionActorName; }
    public void setActionActorName(String v) { this.actionActorName = v; }
    public String getActionActorDisplayName() { return actionActorDisplayName; }
    public void setActionActorDisplayName(String v) { this.actionActorDisplayName = v; }
    public String getActionComments() { return actionComments; }
    public void setActionComments(String v) { this.actionComments = v; }

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
