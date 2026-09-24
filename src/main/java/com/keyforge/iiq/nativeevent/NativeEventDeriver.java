package com.keyforge.iiq.nativeevent;

import com.keyforge.iiq.event.EventDeriver;
import com.keyforge.iiq.event.EventFingerprint;
import com.keyforge.iiq.event.EventRow;
import com.keyforge.iiq.event.EventType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure, DB-free derivation of the native EVENT zone from already-extracted {@code iiq_native} source
 * rows. Produces append-only {@link EventRow}s (reusing the CEC event structure + fingerprint) and
 * explicit-id {@link NativeEventLinkRow}s. Never calls IIQ, never executes a rule, never reads any
 * REST-derived table, and never infers a link from a name/timestamp/order — a link is emitted only when
 * the source row carried an explicit id in the named column.
 *
 * <p>Every event is stamped {@code src_interface = "native_iiq_java_api"} so its native provenance is
 * unambiguous, and {@code src_object_id} preserves the exact native source record id.
 *
 * <p><b>Sources → event types</b> (AuditEvent intentionally excluded):
 * <ul>
 *   <li>{@code kf_identity_request_approval} → APPROVAL_DECISION (end_date present) / APPROVAL_OPEN</li>
 *   <li>{@code kf_task_result} → TASK_COMPLETED (completed present) / TASK_LAUNCHED</li>
 *   <li>{@code kf_provisioning_txn} → PROVISIONING_TXN</li>
 *   <li>{@code kf_workitem_archive} → WORKITEM_ARCHIVED</li>
 * </ul>
 */
public final class NativeEventDeriver {

    public static final String SRC_INTERFACE = "native_iiq_java_api";

    /** Source IIQ class for a certification-item decision event (native-local; not in the REST deriver). */
    public static final String TYPE_CERTIFICATION_ITEM = "sailpoint.object.CertificationItem";
    /** Event type for a certification/remediation decision (emitted only when a decision exists). */
    public static final String CERTIFICATION_DECISION = "CERTIFICATION_DECISION";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NativeEventDeriver() {
    }

    /** Output of a derivation pass. */
    public static final class Derived {
        public final List<EventRow> events = new ArrayList<>();
        public final List<NativeEventLinkRow> links = new ArrayList<>();
    }

    // --- native source snapshots (exactly the persisted native columns used) ---

    /** {@code kf_identity_request_approval}. */
    public record ApprovalSrc(String approvalId, String requestSourceId, String ownerId, String owner,
                              String state, Boolean approved, String completer, String workItemId,
                              LocalDateTime startDate, LocalDateTime endDate) {
    }

    /** {@code kf_task_result}. */
    public record TaskSrc(String taskResultId, String name, String type, String completionStatus,
                          String targetClass, String targetId, LocalDateTime launched, LocalDateTime completed) {
    }

    /** {@code kf_provisioning_txn}. */
    public record ProvSrc(String txnId, String operation, String type, String status, String planResultStatus,
                          String identityName, String applicationName, String accessRequestId, String ownerId,
                          String waitWorkItemId, String manualWorkItemId, LocalDateTime createdAt) {
    }

    /** {@code kf_workitem_archive}. */
    public record ArchiveSrc(String archiveSourceId, String name, String type, String state, String completer,
                             Boolean signed, String identityRequestId, String workItemId, LocalDateTime archivedTs) {
    }

    /**
     * {@code kf_certification_item} — a certification/remediation decision. {@code identity} is the
     * certified identity <b>name</b> (not an id) and is therefore never turned into a link; only the
     * explicit id columns (certification_id, entity_id, owner_id) become links.
     */
    public record CertItemSrc(String certItemId, String certificationId, String entityId, String identity,
                              String actionStatus, String remediationAction, String actorName,
                              Boolean isApproved, Boolean isRemediation, Boolean isRevokeAccount,
                              Boolean actedUpon, String ownerId, LocalDateTime decisionDate,
                              LocalDateTime finishedDate, LocalDateTime completed) {
    }

    public static Derived deriveAll(List<ApprovalSrc> approvals, List<TaskSrc> tasks,
                                    List<ProvSrc> provisioning, List<ArchiveSrc> archives, String runId) {
        return deriveAll(approvals, tasks, provisioning, archives, null, runId);
    }

    public static Derived deriveAll(List<ApprovalSrc> approvals, List<TaskSrc> tasks,
                                    List<ProvSrc> provisioning, List<ArchiveSrc> archives,
                                    List<CertItemSrc> certItems, String runId) {
        Derived d = new Derived();
        if (approvals != null) {
            for (ApprovalSrc s : approvals) {
                approval(s, runId, d);
            }
        }
        if (tasks != null) {
            for (TaskSrc s : tasks) {
                task(s, runId, d);
            }
        }
        if (provisioning != null) {
            for (ProvSrc s : provisioning) {
                provisioning(s, runId, d);
            }
        }
        if (archives != null) {
            for (ArchiveSrc s : archives) {
                archive(s, runId, d);
            }
        }
        if (certItems != null) {
            for (CertItemSrc s : certItems) {
                certItem(s, runId, d);
            }
        }
        return d;
    }

    private static void approval(ApprovalSrc s, String runId, Derived d) {
        boolean decided = s.endDate() != null;
        String type = decided ? EventType.APPROVAL_DECISION : EventType.APPROVAL_OPEN;
        LocalDateTime ts = decided ? s.endDate() : s.startDate();
        String fp = EventFingerprint.fingerprint(type, s.requestSourceId(), s.owner(), s.state(),
                String.valueOf(s.approved()), s.completer(), s.workItemId());
        String eventId = EventFingerprint.eventId(EventFingerprint.SRC_SYSTEM, EventDeriver.TYPE_APPROVAL,
                s.approvalId(), fp);
        ObjectNode detail = MAPPER.createObjectNode();
        detail.put("requestSourceId", s.requestSourceId());
        detail.put("owner", s.owner());
        detail.put("state", s.state());
        detail.put("approved", s.approved());
        detail.put("completer", s.completer());
        detail.put("workItemId", s.workItemId());
        d.events.add(event(eventId, EventDeriver.TYPE_APPROVAL, s.approvalId(), type, fp, ts, runId, detail));
        link(d, eventId, EventDeriver.TYPE_APPROVAL, s.approvalId(), "IdentityRequest", s.requestSourceId(),
                "APPROVAL_OF_REQUEST", runId);
        link(d, eventId, EventDeriver.TYPE_APPROVAL, s.approvalId(), "Identity", s.ownerId(),
                "APPROVAL_OWNER", runId);
        link(d, eventId, EventDeriver.TYPE_APPROVAL, s.approvalId(), "WorkItem", s.workItemId(),
                "APPROVAL_WORKITEM", runId);
    }

    private static void task(TaskSrc s, String runId, Derived d) {
        boolean completed = s.completed() != null;
        String type = completed ? EventType.TASK_COMPLETED : EventType.TASK_LAUNCHED;
        LocalDateTime ts = completed ? s.completed() : s.launched();
        String fp = EventFingerprint.fingerprint(type, s.name(), s.type(), s.completionStatus());
        String eventId = EventFingerprint.eventId(EventFingerprint.SRC_SYSTEM, EventDeriver.TYPE_TASK_RESULT,
                s.taskResultId(), fp);
        ObjectNode detail = MAPPER.createObjectNode();
        detail.put("name", s.name());
        detail.put("type", s.type());
        detail.put("completionStatus", s.completionStatus());
        d.events.add(event(eventId, EventDeriver.TYPE_TASK_RESULT, s.taskResultId(), type, fp, ts, runId, detail));
        // Only an explicit target id yields a link; a target *name* alone never does.
        if (present(s.targetId()) && present(s.targetClass())) {
            link(d, eventId, EventDeriver.TYPE_TASK_RESULT, s.taskResultId(), simpleType(s.targetClass()),
                    s.targetId(), "TASK_TARGET", runId);
        }
    }

    private static void provisioning(ProvSrc s, String runId, Derived d) {
        String type = EventType.PROVISIONING_TXN;
        String fp = EventFingerprint.fingerprint(type, s.operation(), s.type(), s.status(),
                s.planResultStatus(), s.applicationName());
        String eventId = EventFingerprint.eventId(EventFingerprint.SRC_SYSTEM,
                EventDeriver.TYPE_PROVISIONING_TXN, s.txnId(), fp);
        ObjectNode detail = MAPPER.createObjectNode();
        detail.put("operation", s.operation());
        detail.put("type", s.type());
        detail.put("status", s.status());
        detail.put("planResultStatus", s.planResultStatus());
        detail.put("identityName", s.identityName());
        detail.put("applicationName", s.applicationName());
        d.events.add(event(eventId, EventDeriver.TYPE_PROVISIONING_TXN, s.txnId(), type, fp, s.createdAt(), runId, detail));
        link(d, eventId, EventDeriver.TYPE_PROVISIONING_TXN, s.txnId(), "IdentityRequest", s.accessRequestId(),
                "PROVISION_OF_REQUEST", runId);
        link(d, eventId, EventDeriver.TYPE_PROVISIONING_TXN, s.txnId(), "Identity", s.ownerId(),
                "PROVISION_OWNER", runId);
        link(d, eventId, EventDeriver.TYPE_PROVISIONING_TXN, s.txnId(), "WorkItem", s.waitWorkItemId(),
                "PROVISION_WAIT_WORKITEM", runId);
        link(d, eventId, EventDeriver.TYPE_PROVISIONING_TXN, s.txnId(), "WorkItem", s.manualWorkItemId(),
                "PROVISION_MANUAL_WORKITEM", runId);
    }

    private static void archive(ArchiveSrc s, String runId, Derived d) {
        String type = EventType.WORKITEM_ARCHIVED;
        String fp = EventFingerprint.fingerprint(type, s.name(), s.type(), s.state(), s.completer(),
                String.valueOf(s.signed()));
        String eventId = EventFingerprint.eventId(EventFingerprint.SRC_SYSTEM,
                EventDeriver.TYPE_WORKITEM_ARCHIVE, s.archiveSourceId(), fp);
        ObjectNode detail = MAPPER.createObjectNode();
        detail.put("name", s.name());
        detail.put("type", s.type());
        detail.put("state", s.state());
        detail.put("completer", s.completer());
        detail.put("signed", s.signed());
        d.events.add(event(eventId, EventDeriver.TYPE_WORKITEM_ARCHIVE, s.archiveSourceId(), type, fp,
                s.archivedTs(), runId, detail));
        link(d, eventId, EventDeriver.TYPE_WORKITEM_ARCHIVE, s.archiveSourceId(), "IdentityRequest",
                s.identityRequestId(), "WORKITEM_OF_REQUEST", runId);
        link(d, eventId, EventDeriver.TYPE_WORKITEM_ARCHIVE, s.archiveSourceId(), "WorkItem", s.workItemId(),
                "ARCHIVE_OF_WORKITEM", runId);
    }

    /**
     * One CERTIFICATION_DECISION event per certification item that has actually been decided
     * (acted upon, or carrying an action status). Undecided items produce no event — we never fabricate
     * a decision. Links use only the explicit id columns; the identity NAME is never turned into a link.
     */
    private static void certItem(CertItemSrc s, String runId, Derived d) {
        boolean decided = Boolean.TRUE.equals(s.actedUpon()) || present(s.actionStatus());
        if (!decided) {
            return;
        }
        LocalDateTime ts = s.decisionDate() != null ? s.decisionDate()
                : (s.finishedDate() != null ? s.finishedDate() : s.completed());
        String fp = EventFingerprint.fingerprint(CERTIFICATION_DECISION, s.certificationId(), s.actionStatus(),
                s.remediationAction(), String.valueOf(s.isApproved()), String.valueOf(s.isRemediation()),
                String.valueOf(s.isRevokeAccount()), s.identity());
        String eventId = EventFingerprint.eventId(EventFingerprint.SRC_SYSTEM, TYPE_CERTIFICATION_ITEM,
                s.certItemId(), fp);
        ObjectNode detail = MAPPER.createObjectNode();
        detail.put("certificationId", s.certificationId());
        detail.put("entityId", s.entityId());
        detail.put("identity", s.identity());
        detail.put("actionStatus", s.actionStatus());
        detail.put("remediationAction", s.remediationAction());
        detail.put("actorName", s.actorName());
        detail.put("isApproved", s.isApproved());
        detail.put("isRemediation", s.isRemediation());
        detail.put("isRevokeAccount", s.isRevokeAccount());
        d.events.add(event(eventId, TYPE_CERTIFICATION_ITEM, s.certItemId(), CERTIFICATION_DECISION, fp, ts, runId, detail));
        link(d, eventId, TYPE_CERTIFICATION_ITEM, s.certItemId(), "Certification", s.certificationId(),
                "CERT_OF_CERTIFICATION", runId);
        link(d, eventId, TYPE_CERTIFICATION_ITEM, s.certItemId(), "CertificationEntity", s.entityId(),
                "CERT_ITEM_ENTITY", runId);
        link(d, eventId, TYPE_CERTIFICATION_ITEM, s.certItemId(), "Identity", s.ownerId(),
                "CERT_OWNER", runId);
    }

    private static EventRow event(String eventId, String srcObjectType, String srcObjectId, String type,
                                  String fp, LocalDateTime ts, String runId, ObjectNode detail) {
        return new EventRow(eventId, EventFingerprint.SRC_SYSTEM, srcObjectType, srcObjectId, type, fp, ts,
                ts == null ? null : EventRow.PRECISE, runId, SRC_INTERFACE, null, detail.toString());
    }

    /** Adds one explicit link — only when {@code targetId} is a real, non-blank id. */
    private static void link(Derived d, String eventId, String srcObjectType, String srcObjectId,
                             String targetType, String targetId, String linkType, String runId) {
        if (!present(targetId)) {
            return;
        }
        String key = eventId + "|" + linkType + "|" + targetType + "|" + targetId.trim();
        String linkId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        d.links.add(new NativeEventLinkRow(linkId, eventId, srcObjectType, srcObjectId, targetType,
                targetId.trim(), linkType, NativeEventLinkRow.EXPLICIT, runId));
    }

    private static boolean present(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** Last dotted segment of a class name (e.g. {@code sailpoint.object.Identity} → {@code Identity}). */
    private static String simpleType(String className) {
        if (className == null) {
            return null;
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }
}
