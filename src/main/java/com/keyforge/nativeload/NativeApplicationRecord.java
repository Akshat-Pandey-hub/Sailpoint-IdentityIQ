package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native Application row pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_application}. Scalars held directly; nested structures carried as pre-serialized
 * JSON strings destined for {@code jsonb} columns. Pure data holder.
 */
public final class NativeApplicationRecord {

    String sourceId;
    String name;
    String description;
    String type;
    String connector;
    String featuresString;
    String profileClass;
    String proxiedName;
    String cluster;
    String icon;
    String aggregationTypes;
    String beforeProvisioningRule;
    String afterProvisioningRule;
    Integer score;

    Boolean authoritative;
    Boolean caseInsensitive;
    Boolean logical;
    Boolean composite;
    Boolean authenticationResource;
    Boolean activityEnabled;
    Boolean inMaintenance;
    Boolean managesOtherApps;
    Boolean nativeChangeDetectionEnabled;
    Boolean supportsProvisioning;
    Boolean supportsAccountOnly;
    Boolean supportsAdditionalAccounts;
    Boolean supportsAuthenticate;
    Boolean supportsGroupProvisioning;
    Boolean supportsDirectPermissions;
    Boolean syncProvisioning;

    String ownerId;
    String ownerName;

    // nested (jsonb)
    String secondaryOwnersJson;
    String remediatorsJson;
    String dependenciesJson;
    String schemasJson;
    String descriptionsJson;
    String attributesJson;

    // timestamps
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

    public String getName() {
        return name;
    }
}
