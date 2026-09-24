package com.keyforge.nativeload;

import java.time.Instant;

/** One native PolicyViolation row destined for {@code iiq_native.kf_violation}. Pure data holder. */
public final class NativeViolationRecord {

    String sourceId;
    String name;
    String identityId;
    String identityName;
    String policyId;
    String policyName;
    String constraintId;
    String constraintName;
    String status;
    Boolean active;
    String leftBundles;
    String rightBundles;
    String entitlementsMarkedForRemediation;
    String bundlesMarkedForRemediation;
    String relevantAppsJson;
    String violatingEntitlementsJson;
    String argumentsJson;
    Instant created;
    Instant modified;
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }
}
