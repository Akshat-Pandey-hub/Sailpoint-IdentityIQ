package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native Workgroup row pulled from the plugin endpoint and prepared for persistence into
 * {@code iiq_native.kf_workgroup}. Scalars held directly; nested structures carried as pre-serialized
 * JSON strings destined for {@code jsonb} columns. Pure data holder.
 */
public final class NativeWorkgroupRecord {

    String sourceId;
    String name;
    String displayName;
    String displayableName;
    String email;
    String type;
    String description;
    String notificationOption;
    Boolean inactive;
    Boolean workgroup;
    String ownerId;
    String ownerName;

    // nested (jsonb)
    String capabilitiesJson;
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
