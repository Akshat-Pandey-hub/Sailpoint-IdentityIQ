package com.keyforge.nativeload;

import java.time.Instant;

/** One native CertificationEntity row for upsert into iiq_native.kf_certification_entity. Pure data holder. */
public final class NativeCertificationEntityRecord {

    String sourceId;
    String certificationId;
    String identity;
    String application;
    String nativeIdentity;
    String accountGroup;
    String firstName;
    String lastName;
    String fullName;
    String referenceAttribute;
    String schemaObjectType;
    String snapshotId;
    String pendingCertification;
    String type;
    String summaryStatus;
    Boolean entityDelegated;
    String entityDelegationStatus;
    Integer compositeScore;
    String targetId;
    String targetName;
    String targetDisplayName;
    Instant completed;
    Instant created;
    Instant modified;
    String ownerId;
    String ownerName;
    String actionStatus;
    Instant actionDecisionDate;
    String actionRemediationAction;
    String actionActorName;
    String actionActorDisplayName;
    String actionComments;

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
