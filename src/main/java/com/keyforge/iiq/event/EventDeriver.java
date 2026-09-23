package com.keyforge.iiq.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, DB-free projection of the already-persisted normalized event-source rows into append-only
 * {@link EventRow}s. Mirrors the derivation style of the event-link / lineage sidecars: no IIQ calls,
 * no new fields invented, timestamp precision recorded honestly, and source ids preserved.
 *
 * <p>Source object types (IIQ classes) and interfaces follow the approved plan exactly:
 * <ul>
 *   <li>IdentityRequest approval → {@code sailpoint.object.IdentityRequest.Approval} (ui-rest, precise)</li>
 *   <li>TaskResult → {@code sailpoint.object.TaskResult} (scim, precise)</li>
 *   <li>AuditEvent → {@code sailpoint.object.AuditEvent} (classic-ui, minute)</li>
 *   <li>ProvisioningTransaction → {@code sailpoint.object.ProvisioningTransaction} (classic-rest, minute)</li>
 * </ul>
 */
public final class EventDeriver {

    public static final String TYPE_APPROVAL = "sailpoint.object.IdentityRequest.Approval";
    public static final String TYPE_TASK_RESULT = "sailpoint.object.TaskResult";
    public static final String TYPE_AUDIT_EVENT = "sailpoint.object.AuditEvent";
    public static final String TYPE_PROVISIONING_TXN = "sailpoint.object.ProvisioningTransaction";
    public static final String TYPE_WORKITEM_ARCHIVE = "sailpoint.object.WorkItemArchive";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventDeriver() {
    }

    // --- source column snapshots (exactly the persisted columns used) -------

    /** A row of {@code kf_request_approval}. */
    public record ApprovalSrc(String id, String requestId, String requestNumber, String ownerDisplayName,
                              String status, LocalDateTime openDate, LocalDateTime completeDate) {
    }

    /** A row of {@code kf_task_result}. */
    public record TaskSrc(String taskresultid, String completionStatus,
                          LocalDateTime launched, LocalDateTime completed) {
    }

    /** A row of {@code kf_audit_event}. */
    public record AuditSrc(String auditid, String action, String source, String target,
                           LocalDateTime createdAt, String createdDisplay) {
    }

    /** A row of {@code kf_provisioning_txn}. */
    public record ProvSrc(String txnid, String operation, String source, String status, String result,
                          LocalDateTime createdAt, String createdDisplay) {
    }

    /**
     * A row of the native {@code kf_workitem_archive} (CEC/history) table. One archive → one
     * {@code WORKITEM_ARCHIVED} event; sign-offs are summarized in the detail, never exploded into
     * per-sign-off events.
     */
    public record WorkItemArchiveSrc(String archiveId, String name, String type, String state,
                                     String completer, Boolean signed, String targetName,
                                     String identityRequestId, String certificationId,
                                     Integer signOffCount, LocalDateTime archivedTs) {
    }

    /** Derives all events from the four source snapshots (any list may be empty). */
    public static List<EventRow> deriveAll(List<ApprovalSrc> approvals, List<TaskSrc> tasks,
                                           List<AuditSrc> audits, List<ProvSrc> provisioning, String runId) {
        List<EventRow> out = new ArrayList<>();
        if (approvals != null) {
            for (ApprovalSrc s : approvals) {
                out.add(approval(s, runId));
            }
        }
        if (tasks != null) {
            for (TaskSrc s : tasks) {
                out.add(taskResult(s, runId));
            }
        }
        if (audits != null) {
            for (AuditSrc s : audits) {
                out.add(audit(s, runId));
            }
        }
        if (provisioning != null) {
            for (ProvSrc s : provisioning) {
                out.add(provisioning(s, runId));
            }
        }
        return out;
    }

    // --- per-source mappers (precise vs minute recorded honestly) -----------

    public static EventRow approval(ApprovalSrc s, String runId) {
        boolean decided = s.completeDate() != null;
        String eventType = decided ? EventType.APPROVAL_DECISION : EventType.APPROVAL_OPEN;
        LocalDateTime ts = decided ? s.completeDate() : s.openDate();
        String precision = ts != null ? EventRow.PRECISE : null;
        String fp = EventFingerprint.fingerprint(
                s.requestId(), s.ownerDisplayName(), s.status(), str(s.openDate()), str(s.completeDate()));
        ObjectNode detail = MAPPER.createObjectNode();
        put(detail, "requestId", s.requestId());
        put(detail, "requestNumber", s.requestNumber());
        put(detail, "ownerDisplayName", s.ownerDisplayName());
        put(detail, "status", s.status());
        put(detail, "openDate", str(s.openDate()));
        put(detail, "completeDate", str(s.completeDate()));
        return build(TYPE_APPROVAL, s.id(), eventType, fp, ts, precision, runId, "ui-rest", detail);
    }

    public static EventRow taskResult(TaskSrc s, String runId) {
        boolean completed = s.completed() != null;
        String eventType = completed ? EventType.TASK_COMPLETED : EventType.TASK_LAUNCHED;
        LocalDateTime ts = completed ? s.completed() : s.launched();
        String precision = ts != null ? EventRow.PRECISE : null;
        String fp = EventFingerprint.fingerprint(
                s.completionStatus(), str(s.launched()), str(s.completed()));
        ObjectNode detail = MAPPER.createObjectNode();
        put(detail, "completionStatus", s.completionStatus());
        put(detail, "launched", str(s.launched()));
        put(detail, "completed", str(s.completed()));
        return build(TYPE_TASK_RESULT, s.taskresultid(), eventType, fp, ts, precision, runId, "scim", detail);
    }

    public static EventRow audit(AuditSrc s, String runId) {
        LocalDateTime ts = s.createdAt();
        String precision = ts != null ? EventRow.MINUTE : null;
        // Fingerprint uses the verbatim source display timestamp (stable), not the parsed minute value.
        String fp = EventFingerprint.fingerprint(s.action(), s.source(), s.target(), s.createdDisplay());
        ObjectNode detail = MAPPER.createObjectNode();
        put(detail, "action", s.action());
        put(detail, "source", s.source());
        put(detail, "target", s.target());
        put(detail, "created", s.createdDisplay());
        return build(TYPE_AUDIT_EVENT, s.auditid(), EventType.AUDIT_EVENT, fp, ts, precision,
                runId, "classic-ui", detail);
    }

    public static EventRow provisioning(ProvSrc s, String runId) {
        LocalDateTime ts = s.createdAt();
        String precision = ts != null ? EventRow.MINUTE : null;
        String fp = EventFingerprint.fingerprint(
                s.operation(), s.source(), s.status(), s.result(), s.createdDisplay());
        ObjectNode detail = MAPPER.createObjectNode();
        put(detail, "operation", s.operation());
        put(detail, "source", s.source());
        put(detail, "status", s.status());
        put(detail, "result", s.result());
        put(detail, "created", s.createdDisplay());
        return build(TYPE_PROVISIONING_TXN, s.txnid(), EventType.PROVISIONING_TXN, fp, ts, precision,
                runId, "classic-rest", detail);
    }

    // --- helpers ------------------------------------------------------------

    private static EventRow build(String objectType, String objectId, String eventType, String fingerprint,
                                  LocalDateTime ts, String precision, String runId, String srcInterface,
                                  ObjectNode detail) {
        String eventId = EventFingerprint.eventId(EventFingerprint.SRC_SYSTEM, objectType, objectId, fingerprint);
        String detailJson = detail == null || detail.isEmpty() ? null : detail.toString();
        return new EventRow(eventId, EventFingerprint.SRC_SYSTEM, objectType, objectId, eventType, fingerprint,
                ts, precision, runId, srcInterface, null, detailJson);
    }

    private static void put(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String str(LocalDateTime v) {
        return v == null ? "" : v.toString();
    }
}
