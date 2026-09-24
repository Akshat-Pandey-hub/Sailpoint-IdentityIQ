package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeWorkItemRow;

import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Comment;
import sailpoint.object.Identity;
import sailpoint.object.SignOffHistory;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a native live {@code sailpoint.object.WorkItem} into a {@link NativeWorkItemRow}. Read-only; only
 * getters verified against the 8.4 {@code identityiq.jar}. {@code getIdentityRequestId()} is the direct
 * native link to the originating IdentityRequest. Reference getters that may lazy-load (workflow case) are
 * fetched defensively. Nested collections (comments/sign-offs/owner-history/approval-set items) are flattened
 * to plain JSON-safe maps. Nothing is inferred.
 */
public final class NativeWorkItemMapper {

    private NativeWorkItemMapper() {
    }

    public static NativeWorkItemRow map(WorkItem w, String sourceSystem, String extractionRunId) {
        NativeWorkItemRow row = new NativeWorkItemRow();

        row.setSourceId(w.getId());
        row.setName(w.getName());
        row.setType(enumName(w.getType()));
        row.setState(enumName(w.getState()));
        row.setLevel(enumName(w.getLevel()));

        Identity requester = w.getRequester();
        if (requester != null) {
            row.setRequesterId(requester.getId());
            row.setRequesterName(requester.getName());
        }
        Identity assignee = w.getAssignee();
        if (assignee != null) {
            row.setAssigneeId(assignee.getId());
            row.setAssigneeName(assignee.getName());
        }
        Identity owner = w.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }
        row.setCompleter(w.getCompleter());
        row.setCompletionComments(w.getCompletionComments());
        row.setHandler(w.getHandler());
        row.setNotificationName(w.getNotificationName());

        row.setIdentityRequestId(w.getIdentityRequestId());
        row.setTargetId(w.getTargetId());
        row.setTargetName(w.getTargetName());
        row.setCertificationId(w.getCertification());
        row.setCertificationEntityId(w.getCertificationEntity());
        row.setCertificationItemId(w.getCertificationItem());
        row.setEntityType(enumName(w.getEntityType()));
        row.setCertificationRelated(Boolean.valueOf(w.isCertificationRelated()));
        mapWorkflowCase(w, row);

        row.setExpiration(toInstant(w.getExpiration()));
        row.setExpirationDate(toInstant(w.getExpirationDate()));
        row.setNotification(toInstant(w.getNotification()));
        row.setWakeUpDate(toInstant(w.getWakeUpDate()));
        row.setEscalationCount(Integer.valueOf(w.getEscalationCount()));
        row.setReminders(Integer.valueOf(w.getReminders()));
        row.setRemindersSent(Integer.valueOf(w.getRemindersSent()));
        row.setExpired(Boolean.valueOf(w.isExpired()));
        row.setExpirable(Boolean.valueOf(w.isExpirable()));

        mapComments(w.getComments(), row);
        mapSignOffs(w.getSignOffs(), row);
        mapOwnerHistory(w.getOwnerHistory(), row);
        mapApprovalSet(w.getApprovalSet(), row);

        row.setCreated(toInstant(w.getCreated()));
        row.setModified(toInstant(w.getModified()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static void mapWorkflowCase(WorkItem w, NativeWorkItemRow row) {
        try {
            WorkflowCase wc = w.getWorkflowCase();
            if (wc != null) {
                row.setWorkflowCaseId(wc.getId());
                row.setWorkflowCaseName(wc.getName());
            }
        } catch (Throwable t) {
            // an unresolved workflow case never fails the work item
        }
    }

    private static void mapComments(List<Comment> comments, NativeWorkItemRow row) {
        if (comments == null) {
            return;
        }
        for (Comment c : comments) {
            if (c == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("author", c.getAuthor());
            m.put("comment", c.getComment());
            m.put("date", iso(c.getDate()));
            row.getComments().add(m);
        }
    }

    private static void mapSignOffs(List<SignOffHistory> signOffs, NativeWorkItemRow row) {
        if (signOffs == null) {
            return;
        }
        for (SignOffHistory s : signOffs) {
            if (s == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("signerName", s.getSignerName());
            m.put("signerDisplayName", s.getSignerDisplayName());
            m.put("date", iso(s.getDate()));
            m.put("text", s.getText());
            row.getSignOffs().add(m);
        }
    }

    private static void mapOwnerHistory(List<WorkItem.OwnerHistory> history, NativeWorkItemRow row) {
        if (history == null) {
            return;
        }
        for (WorkItem.OwnerHistory h : history) {
            if (h == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("oldOwner", h.getOldOwner());
            m.put("newOwner", h.getNewOwner());
            m.put("source", h.getSource());
            m.put("comment", h.getComment());
            m.put("startDate", iso(h.getStartDate()));
            row.getOwnerHistory().add(m);
        }
    }

    private static void mapApprovalSet(ApprovalSet set, NativeWorkItemRow row) {
        if (set == null || set.getItems() == null) {
            return;
        }
        row.setApprovalSetItemCount(Integer.valueOf(set.getItems().size()));
        for (ApprovalItem ai : set.getItems()) {
            if (ai == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("applicationName", ai.getApplicationName());
            m.put("displayValue", ai.getDisplayValue());
            m.put("operation", ai.getOperation());
            m.put("state", enumName(ai.getState()));
            m.put("approver", ai.getApprover());
            m.put("owner", ai.getOwner());
            row.getApprovalSetItems().add(m);
        }
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String iso(Date d) {
        return d == null ? null : d.toInstant().toString();
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
