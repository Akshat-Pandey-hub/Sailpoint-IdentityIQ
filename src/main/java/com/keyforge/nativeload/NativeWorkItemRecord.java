package com.keyforge.nativeload;

import java.time.Instant;

/** One native WorkItem row for upsert into iiq_native.kf_workitem. Pure data holder. */
public final class NativeWorkItemRecord {

    String sourceId;
    String name;
    String type;
    String state;
    String level;
    String requesterId;
    String requesterName;
    String assigneeId;
    String assigneeName;
    String ownerId;
    String ownerName;
    String completer;
    String completionComments;
    String handler;
    String notificationName;
    String identityRequestId;
    String targetId;
    String targetName;
    String certificationId;
    String certificationEntityId;
    String certificationItemId;
    String entityType;
    Boolean certificationRelated;
    String workflowCaseId;
    String workflowCaseName;
    Instant expiration;
    Instant expirationDate;
    Instant notification;
    Instant wakeUpDate;
    Integer escalationCount;
    Integer reminders;
    Integer remindersSent;
    Boolean expired;
    Boolean expirable;
    Integer approvalSetItemCount;
    String commentsJson;
    String signOffsJson;
    String ownerHistoryJson;
    String approvalSetItemsJson;
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
