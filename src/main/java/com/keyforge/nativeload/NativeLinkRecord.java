package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native Link (account) row pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_account}. Scalars held directly; nested structures carried as pre-serialized
 * JSON strings destined for {@code jsonb} columns. Pure data holder.
 */
public final class NativeLinkRecord {

    String sourceId;
    String uuid;
    String nativeIdentity;
    String displayName;
    String displayableName;
    String instance;
    String componentIds;
    String applicationId;
    String applicationName;
    String identityId;
    String identityName;

    Boolean disabled;
    Boolean locked;
    Boolean composite;
    Boolean manuallyCorrelated;
    Boolean hasEntitlements;
    Boolean iiqDisabled;
    Boolean iiqLocked;

    // nested (jsonb)
    String permissionsJson;
    String targetPermissionsJson;
    String attributesJson;
    String entitlementAttributesJson;

    // timestamps
    Instant created;
    Instant modified;
    Instant lastRefresh;
    Instant lastTargetAggregation;

    // lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }
}
