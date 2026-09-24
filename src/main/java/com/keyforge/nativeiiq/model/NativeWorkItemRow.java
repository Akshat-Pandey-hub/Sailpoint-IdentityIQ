package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a live SailPoint {@code WorkItem} (current/pending work — e.g. an in-flight
 * approval). Completed work items are pruned by IIQ and captured separately as {@code WorkItemArchive}
 * ({@code kf_workitem_archive}); this table is current-state (soft-delete). {@code identityRequestId} is the
 * DIRECT native link to the originating {@code IdentityRequest}, and {@code sourceId} (= WorkItem id) is what
 * {@code kf_identity_request_approval.work_item_id} points at for still-open approvals. Pure data holder.
 */
public final class NativeWorkItemRow {

    private String sourceId;          // getId()
    private String name;              // work item number/name
    private String type;              // WorkItem.Type name
    private String state;             // WorkItem.State name
    private String level;             // WorkItem.Level name (priority — NOT approval level)

    private String requesterId;
    private String requesterName;
    private String assigneeId;
    private String assigneeName;
    private String ownerId;
    private String ownerName;
    private String completer;
    private String completionComments;
    private String handler;
    private String notificationName;

    // native references (source-backed; NULL when absent — never inferred)
    private String identityRequestId;
    private String targetId;
    private String targetName;
    private String certificationId;
    private String certificationEntityId;
    private String certificationItemId;
    private String entityType;
    private Boolean certificationRelated;
    private String workflowCaseId;
    private String workflowCaseName;

    // dates / escalation
    private Instant expiration;
    private Instant expirationDate;
    private Instant notification;
    private Instant wakeUpDate;
    private Integer escalationCount;
    private Integer reminders;
    private Integer remindersSent;
    private Boolean expired;
    private Boolean expirable;

    // nested (jsonb)
    private Integer approvalSetItemCount;
    private final List<Map<String, Object>> comments = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> signOffs = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> ownerHistory = new ArrayList<Map<String, Object>>();
    private final List<Map<String, Object>> approvalSetItems = new ArrayList<Map<String, Object>>();

    private Instant created;
    private Instant modified;

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.WorkItem";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getLevel() { return level; }
    public void setLevel(String v) { this.level = v; }

    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String v) { this.requesterId = v; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String v) { this.requesterName = v; }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String v) { this.assigneeId = v; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String v) { this.assigneeName = v; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }
    public String getCompleter() { return completer; }
    public void setCompleter(String v) { this.completer = v; }
    public String getCompletionComments() { return completionComments; }
    public void setCompletionComments(String v) { this.completionComments = v; }
    public String getHandler() { return handler; }
    public void setHandler(String v) { this.handler = v; }
    public String getNotificationName() { return notificationName; }
    public void setNotificationName(String v) { this.notificationName = v; }

    public String getIdentityRequestId() { return identityRequestId; }
    public void setIdentityRequestId(String v) { this.identityRequestId = v; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String v) { this.targetName = v; }
    public String getCertificationId() { return certificationId; }
    public void setCertificationId(String v) { this.certificationId = v; }
    public String getCertificationEntityId() { return certificationEntityId; }
    public void setCertificationEntityId(String v) { this.certificationEntityId = v; }
    public String getCertificationItemId() { return certificationItemId; }
    public void setCertificationItemId(String v) { this.certificationItemId = v; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String v) { this.entityType = v; }
    public Boolean getCertificationRelated() { return certificationRelated; }
    public void setCertificationRelated(Boolean v) { this.certificationRelated = v; }
    public String getWorkflowCaseId() { return workflowCaseId; }
    public void setWorkflowCaseId(String v) { this.workflowCaseId = v; }
    public String getWorkflowCaseName() { return workflowCaseName; }
    public void setWorkflowCaseName(String v) { this.workflowCaseName = v; }

    public Instant getExpiration() { return expiration; }
    public void setExpiration(Instant v) { this.expiration = v; }
    public Instant getExpirationDate() { return expirationDate; }
    public void setExpirationDate(Instant v) { this.expirationDate = v; }
    public Instant getNotification() { return notification; }
    public void setNotification(Instant v) { this.notification = v; }
    public Instant getWakeUpDate() { return wakeUpDate; }
    public void setWakeUpDate(Instant v) { this.wakeUpDate = v; }
    public Integer getEscalationCount() { return escalationCount; }
    public void setEscalationCount(Integer v) { this.escalationCount = v; }
    public Integer getReminders() { return reminders; }
    public void setReminders(Integer v) { this.reminders = v; }
    public Integer getRemindersSent() { return remindersSent; }
    public void setRemindersSent(Integer v) { this.remindersSent = v; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean v) { this.expired = v; }
    public Boolean getExpirable() { return expirable; }
    public void setExpirable(Boolean v) { this.expirable = v; }

    public Integer getApprovalSetItemCount() { return approvalSetItemCount; }
    public void setApprovalSetItemCount(Integer v) { this.approvalSetItemCount = v; }
    public List<Map<String, Object>> getComments() { return comments; }
    public List<Map<String, Object>> getSignOffs() { return signOffs; }
    public List<Map<String, Object>> getOwnerHistory() { return ownerHistory; }
    public List<Map<String, Object>> getApprovalSetItems() { return approvalSetItems; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }
    public String getSrcInterface() { return srcInterface; }
    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }
    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }
    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }

    public Map<String, Object> newNested() { return new LinkedHashMap<String, Object>(); }
}
