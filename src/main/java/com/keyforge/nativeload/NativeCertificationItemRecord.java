package com.keyforge.nativeload;

import java.time.Instant;

/** One native CertificationItem row (with folded decision/action) for upsert into kf_certification_item. */
public final class NativeCertificationItemRecord {

    String sourceId;
    String certificationId;
    String entityId;
    String identity;
    String type;
    String subType;
    String bundle;
    String bundleAssignmentId;
    String exceptionApplication;
    String exceptionAttributeName;
    String exceptionAttributeValue;
    String exceptionPermissionTarget;
    String exceptionPermissionRight;
    String accountGroup;
    String phase;
    String summaryStatus;
    Instant completed;
    Instant lastDecision;
    Instant expirationDate;
    Instant finishedDate;
    Boolean iiqElevatedAccess;
    Boolean reviewed;
    Boolean delegated;
    Boolean actedUpon;
    Boolean historical;
    Boolean expired;
    String targetId;
    String targetName;
    String shortDescription;
    String violationSummary;
    String applicationNamesJson;
    String classificationNamesJson;
    String actionStatus;
    Instant actionDecisionDate;
    String actionDecisionCertificationId;
    String actionRemediationAction;
    String actionActorName;
    String actionActorDisplayName;
    String actionComments;
    String actionCompletionComments;
    String actionOwnerName;
    Instant actionMitigationExpiration;
    Boolean actionIsApproved;
    Boolean actionIsRemediation;
    Boolean actionIsMitigation;
    Boolean actionIsDelegation;
    Boolean actionIsRevokeAccount;
    Boolean actionIsAutoDecision;
    Boolean actionIsBulkCertified;
    String ownerId;
    String ownerName;
    String policyViolationId;
    String roleAssignment;
    Instant created;
    Instant modified;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getIdentity() {
        return identity;
    }
}
