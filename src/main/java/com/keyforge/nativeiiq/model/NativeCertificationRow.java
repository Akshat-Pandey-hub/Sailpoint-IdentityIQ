package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code Certification} — a single certification (per-certifier)
 * within a campaign group. Current-state (mutable) data. Pure data holder. CertificationGroup and
 * CertificationDefinition are preserved as reference columns rather than nested objects. Nothing inferred.
 */
public final class NativeCertificationRow {

    private String sourceId;
    private String name;
    private String certificationName;
    private String shortName;
    private String type;              // Certification.Type name
    private String phase;             // Certification.Phase name
    private String comments;
    private String creator;
    private String manager;

    // references (ids/names only)
    private String certificationGroupId;
    private String certificationGroupName;
    private String certificationDefinitionId;
    private String groupDefinitionId;
    private String groupDefinitionName;
    private String applicationId;
    private String taskScheduleId;
    private String triggerId;
    private String parentId;

    // flags
    private Boolean complete;
    private Boolean expired;
    private Boolean continuous;
    private Boolean electronicallySigned;

    // dates
    private Instant signed;
    private Instant finished;
    private Instant activated;
    private Instant expiration;
    private Instant created;
    private Instant modified;

    // statistics (source-computed counters)
    private Integer totalItems;
    private Integer completedItems;
    private Integer openItems;
    private Integer totalEntities;
    private Integer completedEntities;
    private Integer openEntities;
    private Integer percentComplete;

    // lists (jsonb)
    private final List<String> certifiers = new ArrayList<String>();
    private final List<Map<String, Object>> signOffHistory = new ArrayList<Map<String, Object>>();

    private String ownerId;
    private String ownerName;

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Certification";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCertificationName() { return certificationName; }
    public void setCertificationName(String v) { this.certificationName = v; }
    public String getShortName() { return shortName; }
    public void setShortName(String v) { this.shortName = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
    public String getCreator() { return creator; }
    public void setCreator(String v) { this.creator = v; }
    public String getManager() { return manager; }
    public void setManager(String v) { this.manager = v; }

    public String getCertificationGroupId() { return certificationGroupId; }
    public void setCertificationGroupId(String v) { this.certificationGroupId = v; }
    public String getCertificationGroupName() { return certificationGroupName; }
    public void setCertificationGroupName(String v) { this.certificationGroupName = v; }
    public String getCertificationDefinitionId() { return certificationDefinitionId; }
    public void setCertificationDefinitionId(String v) { this.certificationDefinitionId = v; }
    public String getGroupDefinitionId() { return groupDefinitionId; }
    public void setGroupDefinitionId(String v) { this.groupDefinitionId = v; }
    public String getGroupDefinitionName() { return groupDefinitionName; }
    public void setGroupDefinitionName(String v) { this.groupDefinitionName = v; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }
    public String getTaskScheduleId() { return taskScheduleId; }
    public void setTaskScheduleId(String v) { this.taskScheduleId = v; }
    public String getTriggerId() { return triggerId; }
    public void setTriggerId(String v) { this.triggerId = v; }
    public String getParentId() { return parentId; }
    public void setParentId(String v) { this.parentId = v; }

    public Boolean getComplete() { return complete; }
    public void setComplete(Boolean v) { this.complete = v; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean v) { this.expired = v; }
    public Boolean getContinuous() { return continuous; }
    public void setContinuous(Boolean v) { this.continuous = v; }
    public Boolean getElectronicallySigned() { return electronicallySigned; }
    public void setElectronicallySigned(Boolean v) { this.electronicallySigned = v; }

    public Instant getSigned() { return signed; }
    public void setSigned(Instant v) { this.signed = v; }
    public Instant getFinished() { return finished; }
    public void setFinished(Instant v) { this.finished = v; }
    public Instant getActivated() { return activated; }
    public void setActivated(Instant v) { this.activated = v; }
    public Instant getExpiration() { return expiration; }
    public void setExpiration(Instant v) { this.expiration = v; }
    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public Integer getTotalItems() { return totalItems; }
    public void setTotalItems(Integer v) { this.totalItems = v; }
    public Integer getCompletedItems() { return completedItems; }
    public void setCompletedItems(Integer v) { this.completedItems = v; }
    public Integer getOpenItems() { return openItems; }
    public void setOpenItems(Integer v) { this.openItems = v; }
    public Integer getTotalEntities() { return totalEntities; }
    public void setTotalEntities(Integer v) { this.totalEntities = v; }
    public Integer getCompletedEntities() { return completedEntities; }
    public void setCompletedEntities(Integer v) { this.completedEntities = v; }
    public Integer getOpenEntities() { return openEntities; }
    public void setOpenEntities(Integer v) { this.openEntities = v; }
    public Integer getPercentComplete() { return percentComplete; }
    public void setPercentComplete(Integer v) { this.percentComplete = v; }

    public List<String> getCertifiers() { return certifiers; }
    public List<Map<String, Object>> getSignOffHistory() { return signOffHistory; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }
    public String getSrcInterface() { return srcInterface; }
    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }
    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }
    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
