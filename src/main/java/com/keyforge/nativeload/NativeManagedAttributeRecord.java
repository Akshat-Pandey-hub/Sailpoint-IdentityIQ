package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native ManagedAttribute row pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_entitlement}. Scalars held directly; nested structures carried as pre-serialized
 * JSON strings destined for {@code jsonb} columns. Pure data holder.
 */
public final class NativeManagedAttributeRecord {

    String sourceId;
    String name;
    String value;
    String displayName;
    String displayableName;
    String attribute;
    String type;
    String uuid;
    String referenceAttribute;
    String purview;
    String applicationId;
    String applicationName;
    String instance;
    String nativeIdentity;

    Boolean requestable;
    Boolean group;
    Boolean permission;
    Boolean uncorrelated;
    Boolean aggregated;
    Boolean iiqElevatedAccess;

    String ownerId;
    String ownerName;
    String description;

    // nested (jsonb)
    String descriptionsJson;
    String permissionsJson;
    String targetPermissionsJson;
    String inheritanceJson;
    String associationsJson;
    String attributesJson;

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

    public String getName() {
        return name;
    }
}
