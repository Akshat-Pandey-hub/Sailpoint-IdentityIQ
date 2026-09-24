package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeWorkItemExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkItemRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON wire envelope for native WorkItem. Field names are the contract for NativeWorkItemFields. */
public final class NativeWorkItemWire {

    private NativeWorkItemWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "WorkItem");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeWorkItemExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeWorkItemRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "WorkItem");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeWorkItemRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("type", r.getType());
        m.put("state", r.getState());
        m.put("level", r.getLevel());
        m.put("requesterId", r.getRequesterId());
        m.put("requesterName", r.getRequesterName());
        m.put("assigneeId", r.getAssigneeId());
        m.put("assigneeName", r.getAssigneeName());
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("completer", r.getCompleter());
        m.put("completionComments", r.getCompletionComments());
        m.put("handler", r.getHandler());
        m.put("notificationName", r.getNotificationName());
        m.put("identityRequestId", r.getIdentityRequestId());
        m.put("targetId", r.getTargetId());
        m.put("targetName", r.getTargetName());
        m.put("certificationId", r.getCertificationId());
        m.put("certificationEntityId", r.getCertificationEntityId());
        m.put("certificationItemId", r.getCertificationItemId());
        m.put("entityType", r.getEntityType());
        m.put("certificationRelated", r.getCertificationRelated());
        m.put("workflowCaseId", r.getWorkflowCaseId());
        m.put("workflowCaseName", r.getWorkflowCaseName());
        m.put("expiration", iso(r.getExpiration()));
        m.put("expirationDate", iso(r.getExpirationDate()));
        m.put("notification", iso(r.getNotification()));
        m.put("wakeUpDate", iso(r.getWakeUpDate()));
        m.put("escalationCount", r.getEscalationCount());
        m.put("reminders", r.getReminders());
        m.put("remindersSent", r.getRemindersSent());
        m.put("expired", r.getExpired());
        m.put("expirable", r.getExpirable());
        m.put("approvalSetItemCount", r.getApprovalSetItemCount());
        m.put("comments", new ArrayList<Map<String, Object>>(r.getComments()));
        m.put("signOffs", new ArrayList<Map<String, Object>>(r.getSignOffs()));
        m.put("ownerHistory", new ArrayList<Map<String, Object>>(r.getOwnerHistory()));
        m.put("approvalSetItems", new ArrayList<Map<String, Object>>(r.getApprovalSetItems()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
