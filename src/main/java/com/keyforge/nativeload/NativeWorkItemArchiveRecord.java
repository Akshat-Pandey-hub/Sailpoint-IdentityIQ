package com.keyforge.nativeload;

import java.time.Instant;

/**
 * One native WorkItemArchive row pulled from the plugin endpoint and prepared for append into
 * {@code iiq_native.kf_workitem_archive}. Scalars held directly; nested structures carried as
 * pre-serialized JSON strings destined for {@code jsonb} columns. Pure data holder.
 *
 * <p>These records are immutable CEC/history rows: the archive {@code sourceId} is the canonical identity
 * and {@code archived} is the authoritative historical archival timestamp (mirrored into {@code srcEventTs}).
 */
public final class NativeWorkItemArchiveRecord {

    String sourceId;
    String workItemId;
    String name;
    String type;
    String state;
    String level;
    String requester;
    String assignee;
    String ownerName;
    String completer;
    String completionComments;
    Boolean signed;
    String targetClass;
    String targetId;
    String targetName;
    String identityRequestId;
    String certificationId;
    String certificationEntityId;
    String certificationItemId;
    String entityType;

    // nested (jsonb)
    String signOffsJson;
    String commentsJson;
    String ownerHistoryJson;
    String systemAttributesJson;
    String attributesJson;

    // timestamps
    Instant created;
    Instant modified;
    Instant expiration;
    Instant archived;

    // CEC lineage envelope
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String srcNaturalKey;
    Instant srcEventTs;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }

    public Instant getArchived() { return archived; }
    public Instant getSrcEventTs() { return srcEventTs; }
    public String getSrcObjectId() { return sourceId; }
    public String getSrcObjectType() { return srcObjectType; }
    public String getExtractionRunId() { return extractionRunId; }
    public String getSrcSystem() { return srcSystem; }
    public String getSrcInterface() { return srcInterface; }
}
