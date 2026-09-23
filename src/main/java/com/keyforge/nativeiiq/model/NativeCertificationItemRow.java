package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Native-source projection of a SailPoint {@code CertificationItem} — one reviewed access item, with its
 * 1:1 {@code CertificationAction} (the decision) folded in as {@code action_*} fields. Current-state.
 * The certified target is preserved verbatim (bundle / exception application+attribute+value / permission)
 * so it links back to {@code kf_identity_entitlement}. Nothing inferred; a native {@code null} stays null.
 */
public final class NativeCertificationItemRow {

    private String sourceId;
    private String certificationId;
    private String entityId;
    private String identity;
    private String type;
    private String subType;
    private String bundle;
    private String bundleAssignmentId;
    private String exceptionApplication;
    private String exceptionAttributeName;
    private String exceptionAttributeValue;
    private String exceptionPermissionTarget;
    private String exceptionPermissionRight;
    private String accountGroup;
    private String phase;
    private String summaryStatus;
    private Instant completed;
    private Instant lastDecision;
    private Instant expirationDate;
    private Instant finishedDate;
    private Boolean iiqElevatedAccess;
    private Boolean reviewed;
    private Boolean delegated;
    private Boolean actedUpon;
    private Boolean historical;
    private Boolean expired;
    private String targetId;
    private String targetName;
    private String shortDescription;
    private String violationSummary;
    private final List<String> applicationNames = new ArrayList<String>();
    private final List<String> classificationNames = new ArrayList<String>();

    // decision / action (folded from getAction())
    private String actionStatus;
    private Instant actionDecisionDate;
    private String actionDecisionCertificationId;
    private String actionRemediationAction;
    private String actionActorName;
    private String actionActorDisplayName;
    private String actionComments;
    private String actionCompletionComments;
    private String actionOwnerName;
    private Instant actionMitigationExpiration;
    private Boolean actionIsApproved;
    private Boolean actionIsRemediation;
    private Boolean actionIsMitigation;
    private Boolean actionIsDelegation;
    private Boolean actionIsRevokeAccount;
    private Boolean actionIsAutoDecision;
    private Boolean actionIsBulkCertified;

    private String ownerId;
    private String ownerName;
    private Instant created;
    private Instant modified;

    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.CertificationItem";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getCertificationId() { return certificationId; }
    public void setCertificationId(String v) { this.certificationId = v; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String v) { this.entityId = v; }
    public String getIdentity() { return identity; }
    public void setIdentity(String v) { this.identity = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getSubType() { return subType; }
    public void setSubType(String v) { this.subType = v; }
    public String getBundle() { return bundle; }
    public void setBundle(String v) { this.bundle = v; }
    public String getBundleAssignmentId() { return bundleAssignmentId; }
    public void setBundleAssignmentId(String v) { this.bundleAssignmentId = v; }
    public String getExceptionApplication() { return exceptionApplication; }
    public void setExceptionApplication(String v) { this.exceptionApplication = v; }
    public String getExceptionAttributeName() { return exceptionAttributeName; }
    public void setExceptionAttributeName(String v) { this.exceptionAttributeName = v; }
    public String getExceptionAttributeValue() { return exceptionAttributeValue; }
    public void setExceptionAttributeValue(String v) { this.exceptionAttributeValue = v; }
    public String getExceptionPermissionTarget() { return exceptionPermissionTarget; }
    public void setExceptionPermissionTarget(String v) { this.exceptionPermissionTarget = v; }
    public String getExceptionPermissionRight() { return exceptionPermissionRight; }
    public void setExceptionPermissionRight(String v) { this.exceptionPermissionRight = v; }
    public String getAccountGroup() { return accountGroup; }
    public void setAccountGroup(String v) { this.accountGroup = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase = v; }
    public String getSummaryStatus() { return summaryStatus; }
    public void setSummaryStatus(String v) { this.summaryStatus = v; }
    public Instant getCompleted() { return completed; }
    public void setCompleted(Instant v) { this.completed = v; }
    public Instant getLastDecision() { return lastDecision; }
    public void setLastDecision(Instant v) { this.lastDecision = v; }
    public Instant getExpirationDate() { return expirationDate; }
    public void setExpirationDate(Instant v) { this.expirationDate = v; }
    public Instant getFinishedDate() { return finishedDate; }
    public void setFinishedDate(Instant v) { this.finishedDate = v; }
    public Boolean getIiqElevatedAccess() { return iiqElevatedAccess; }
    public void setIiqElevatedAccess(Boolean v) { this.iiqElevatedAccess = v; }
    public Boolean getReviewed() { return reviewed; }
    public void setReviewed(Boolean v) { this.reviewed = v; }
    public Boolean getDelegated() { return delegated; }
    public void setDelegated(Boolean v) { this.delegated = v; }
    public Boolean getActedUpon() { return actedUpon; }
    public void setActedUpon(Boolean v) { this.actedUpon = v; }
    public Boolean getHistorical() { return historical; }
    public void setHistorical(Boolean v) { this.historical = v; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean v) { this.expired = v; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String v) { this.targetName = v; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String v) { this.shortDescription = v; }
    public String getViolationSummary() { return violationSummary; }
    public void setViolationSummary(String v) { this.violationSummary = v; }
    public List<String> getApplicationNames() { return applicationNames; }
    public List<String> getClassificationNames() { return classificationNames; }

    public String getActionStatus() { return actionStatus; }
    public void setActionStatus(String v) { this.actionStatus = v; }
    public Instant getActionDecisionDate() { return actionDecisionDate; }
    public void setActionDecisionDate(Instant v) { this.actionDecisionDate = v; }
    public String getActionDecisionCertificationId() { return actionDecisionCertificationId; }
    public void setActionDecisionCertificationId(String v) { this.actionDecisionCertificationId = v; }
    public String getActionRemediationAction() { return actionRemediationAction; }
    public void setActionRemediationAction(String v) { this.actionRemediationAction = v; }
    public String getActionActorName() { return actionActorName; }
    public void setActionActorName(String v) { this.actionActorName = v; }
    public String getActionActorDisplayName() { return actionActorDisplayName; }
    public void setActionActorDisplayName(String v) { this.actionActorDisplayName = v; }
    public String getActionComments() { return actionComments; }
    public void setActionComments(String v) { this.actionComments = v; }
    public String getActionCompletionComments() { return actionCompletionComments; }
    public void setActionCompletionComments(String v) { this.actionCompletionComments = v; }
    public String getActionOwnerName() { return actionOwnerName; }
    public void setActionOwnerName(String v) { this.actionOwnerName = v; }
    public Instant getActionMitigationExpiration() { return actionMitigationExpiration; }
    public void setActionMitigationExpiration(Instant v) { this.actionMitigationExpiration = v; }
    public Boolean getActionIsApproved() { return actionIsApproved; }
    public void setActionIsApproved(Boolean v) { this.actionIsApproved = v; }
    public Boolean getActionIsRemediation() { return actionIsRemediation; }
    public void setActionIsRemediation(Boolean v) { this.actionIsRemediation = v; }
    public Boolean getActionIsMitigation() { return actionIsMitigation; }
    public void setActionIsMitigation(Boolean v) { this.actionIsMitigation = v; }
    public Boolean getActionIsDelegation() { return actionIsDelegation; }
    public void setActionIsDelegation(Boolean v) { this.actionIsDelegation = v; }
    public Boolean getActionIsRevokeAccount() { return actionIsRevokeAccount; }
    public void setActionIsRevokeAccount(Boolean v) { this.actionIsRevokeAccount = v; }
    public Boolean getActionIsAutoDecision() { return actionIsAutoDecision; }
    public void setActionIsAutoDecision(Boolean v) { this.actionIsAutoDecision = v; }
    public Boolean getActionIsBulkCertified() { return actionIsBulkCertified; }
    public void setActionIsBulkCertified(Boolean v) { this.actionIsBulkCertified = v; }

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
