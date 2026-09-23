package com.keyforge.nativeload;

import java.time.Instant;

/** One native Certification row for upsert into iiq_native.kf_certification. Pure data holder. */
public final class NativeCertificationRecord {

    String sourceId;
    String name;
    String certificationName;
    String shortName;
    String type;
    String phase;
    String comments;
    String creator;
    String manager;
    String certificationGroupId;
    String certificationGroupName;
    String certificationDefinitionId;
    String groupDefinitionId;
    String groupDefinitionName;
    String applicationId;
    String taskScheduleId;
    String triggerId;
    String parentId;
    Boolean complete;
    Boolean expired;
    Boolean continuous;
    Boolean electronicallySigned;
    Instant signed;
    Instant finished;
    Instant activated;
    Instant expiration;
    Instant created;
    Instant modified;
    Integer totalItems;
    Integer completedItems;
    Integer openItems;
    Integer totalEntities;
    Integer completedEntities;
    Integer openEntities;
    Integer percentComplete;
    String certifiersJson;
    String signOffHistoryJson;
    String ownerId;
    String ownerName;

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
