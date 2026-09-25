package com.keyforge.nativeload;

import java.time.Instant;

/** One native Policy row destined for {@code iiq_native.kf_policy}. Pure data holder. */
public final class NativePolicyRecord {

    String sourceId;
    String name;
    String type;
    String typeKey;
    String description;
    String descriptionsJson;
    String executor;
    String violationOwnerId;
    String violationOwnerName;
    Integer constraintCount;
    String state;
    String violationRule;
    String violationWorkflow;
    String signature;
    String certificationActions;
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
