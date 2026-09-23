package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code WorkItemArchive} — the immutable historical record of a
 * completed/archived work item (approvals, certifications sign-offs, forms, …). This is a <b>CEC / history</b>
 * entity, not a current-state one: an archive row is written once when the work item is archived and is
 * never mutated or deleted. Pure data holder (no SailPoint dependency).
 *
 * <p>The archive's own {@code getId()} is the canonical stable identity (NOT {@code getWorkItemId()}, which
 * is the id of the now-deleted live work item). {@code getArchived()} is the authoritative historical
 * archival timestamp and is carried as {@code srcEventTs}.
 */
public final class NativeWorkItemArchiveRow {

    // --- identity / core ---
    private String sourceId;        // archive getId() — canonical stable identity
    private String workItemId;      // getWorkItemId() — id of the (deleted) live work item
    private String name;            // archive natural key (getName())
    private String type;            // WorkItem.Type name
    private String state;           // WorkItem.State name
    private String level;           // WorkItem.Level name

    // --- actors ---
    private String requester;
    private String assignee;
    private String ownerName;
    private String completer;
    private String completionComments;
    private Boolean signed;

    // --- target reference ---
    private String targetClass;
    private String targetId;
    private String targetName;

    // --- request / certification linkage (all source ids/strings) ---
    private String identityRequestId;
    private String certificationId;
    private String certificationEntityId;
    private String certificationItemId;
    private String entityType;      // CertificationEntity.Type name

    // --- nested (jsonb) — built JSON-safe in the mapper ---
    private final List<Map<String, Object>> signOffs = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> comments = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> ownerHistory = new ArrayList<Map<String, Object>>();
    private final Map<String, Object> systemAttributes = new LinkedHashMap<String, Object>();
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    // --- source timestamps ---
    private Instant created;        // getCreated()
    private Instant modified;       // getModified()
    private Instant expiration;     // getExpiration()
    private Instant archived;       // getArchived() — authoritative historical archival time (= srcEventTs)

    // --- CEC lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.WorkItemArchive";
    private String srcNaturalKey;   // the archive's name
    private Instant srcEventTs;     // = archived
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String v) { this.workItemId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getState() { return state; }
    public void setState(String v) { this.state = v; }

    public String getLevel() { return level; }
    public void setLevel(String v) { this.level = v; }

    public String getRequester() { return requester; }
    public void setRequester(String v) { this.requester = v; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String v) { this.assignee = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public String getCompleter() { return completer; }
    public void setCompleter(String v) { this.completer = v; }

    public String getCompletionComments() { return completionComments; }
    public void setCompletionComments(String v) { this.completionComments = v; }

    public Boolean getSigned() { return signed; }
    public void setSigned(Boolean v) { this.signed = v; }

    public String getTargetClass() { return targetClass; }
    public void setTargetClass(String v) { this.targetClass = v; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String v) { this.targetName = v; }

    public String getIdentityRequestId() { return identityRequestId; }
    public void setIdentityRequestId(String v) { this.identityRequestId = v; }

    public String getCertificationId() { return certificationId; }
    public void setCertificationId(String v) { this.certificationId = v; }

    public String getCertificationEntityId() { return certificationEntityId; }
    public void setCertificationEntityId(String v) { this.certificationEntityId = v; }

    public String getCertificationItemId() { return certificationItemId; }
    public void setCertificationItemId(String v) { this.certificationItemId = v; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String v) { this.entityType = v; }

    public List<Map<String, Object>> getSignOffs() { return signOffs; }
    public List<Map<String, Object>> getComments() { return comments; }
    public List<Map<String, Object>> getOwnerHistory() { return ownerHistory; }
    public Map<String, Object> getSystemAttributes() { return systemAttributes; }
    public Map<String, Object> getAttributes() { return attributes; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }

    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public Instant getExpiration() { return expiration; }
    public void setExpiration(Instant v) { this.expiration = v; }

    public Instant getArchived() { return archived; }
    public void setArchived(Instant v) { this.archived = v; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }

    public String getSrcInterface() { return srcInterface; }

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }

    public String getSrcNaturalKey() { return srcNaturalKey; }
    public void setSrcNaturalKey(String v) { this.srcNaturalKey = v; }

    public Instant getSrcEventTs() { return srcEventTs; }
    public void setSrcEventTs(Instant v) { this.srcEventTs = v; }

    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }

    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
