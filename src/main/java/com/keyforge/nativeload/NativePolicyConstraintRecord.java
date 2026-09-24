package com.keyforge.nativeload;

import java.time.Instant;

/** One native policy-constraint row destined for {@code iiq_native.kf_policy_constraint}. Data holder. */
public final class NativePolicyConstraintRecord {

    String sourceId;
    String policyId;
    String policyName;
    String name;
    String description;
    String constraintType;
    Integer weight;
    String compensatingControl;
    String violationOwnerId;
    String violationOwnerName;
    String violationOwnerType;
    String leftBundlesJson;
    String rightBundlesJson;
    String selectorsJson;
    Integer selectorCount;
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
