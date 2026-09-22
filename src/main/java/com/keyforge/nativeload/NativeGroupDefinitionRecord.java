package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native GroupDefinition (Population/Group) row pulled from the plugin endpoint and prepared for
 * persistence into {@code iiq_native.kf_group_definition}. All fields are scalar. Pure data holder.
 */
public final class NativeGroupDefinitionRecord {

    String sourceId;
    String name;
    String type;
    String factoryId;
    String factoryName;
    String filterExpression;
    Boolean isPrivate;
    Boolean indexed;
    Boolean nullGroup;
    Boolean nameUnique;
    String ownerId;
    String ownerName;
    Instant lastRefresh;
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

    public String getType() {
        return type;
    }
}
