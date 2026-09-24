package com.keyforge.nativeload;

import java.time.Instant;

/** One native identity-role edge for upsert into iiq_native.kf_identity_role. Pure data holder. */
public final class NativeIdentityRoleRecord {

    String identityId;
    String identityName;
    String roleId;
    String roleName;
    String relationshipType;
    String assignmentId;
    String detectionAssignmentIds;
    String comments;
    Boolean futureAssignment;
    Boolean promotedSoftPermit;
    Instant detectionDate;
    String targetsJson;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getIdentityId() {
        return identityId;
    }

    public String getRoleName() {
        return roleName;
    }
}
