package com.keyforge.iiq.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the exact approved source→event mappings: object types, event types, timestamp source and
 * precision markers (precise for approval/task, minute for audit/provisioning, NULL when absent), and
 * that a changed source attribute yields a new event id. No fields are invented.
 */
class EventDeriverTest {

    private static final String RUN = "run-123";
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 1, 10, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 1, 12, 30, 0);

    @Test
    void approvalDecisionIsPreciseWhenCompleted() {
        EventRow r = EventDeriver.approval(new EventDeriver.ApprovalSrc(
                "appr-1", "req-1", "REQ-1", "Molly J", "Approved", T1, T2), RUN);
        assertEquals(EventDeriver.TYPE_APPROVAL, r.srcObjectType());
        assertEquals("appr-1", r.srcObjectId());
        assertEquals(EventType.APPROVAL_DECISION, r.eventType());
        assertEquals(T2, r.srcEventTs());           // complete_date wins
        assertEquals(EventRow.PRECISE, r.srcEventTsPrecision());
        assertEquals("ui-rest", r.srcInterface());
        assertNull(r.rawRef());
        assertEquals(RUN, r.extractionRunId());
    }

    @Test
    void approvalOpenWhenNotCompleted() {
        EventRow r = EventDeriver.approval(new EventDeriver.ApprovalSrc(
                "appr-2", "req-2", "REQ-2", "Alex", "Pending", T1, null), RUN);
        assertEquals(EventType.APPROVAL_OPEN, r.eventType());
        assertEquals(T1, r.srcEventTs());
        assertEquals(EventRow.PRECISE, r.srcEventTsPrecision());
    }

    @Test
    void taskResultPreciseCompletedVsLaunched() {
        EventRow done = EventDeriver.taskResult(new EventDeriver.TaskSrc("t1", "Success", T1, T2), RUN);
        assertEquals(EventDeriver.TYPE_TASK_RESULT, done.srcObjectType());
        assertEquals(EventType.TASK_COMPLETED, done.eventType());
        assertEquals(T2, done.srcEventTs());
        assertEquals(EventRow.PRECISE, done.srcEventTsPrecision());
        assertEquals("scim", done.srcInterface());

        EventRow launched = EventDeriver.taskResult(new EventDeriver.TaskSrc("t2", null, T1, null), RUN);
        assertEquals(EventType.TASK_LAUNCHED, launched.eventType());
        assertEquals(T1, launched.srcEventTs());
    }

    @Test
    void auditEventIsMinutePrecision() {
        EventRow r = EventDeriver.audit(new EventDeriver.AuditSrc(
                "aud-1", "EntitlementAdd", "spadmin", "Identity:Joe", T1, "September 1, 2026, 10:00 AM"), RUN);
        assertEquals(EventDeriver.TYPE_AUDIT_EVENT, r.srcObjectType());
        assertEquals(EventType.AUDIT_EVENT, r.eventType());
        assertEquals(T1, r.srcEventTs());
        assertEquals(EventRow.MINUTE, r.srcEventTsPrecision());
        assertEquals("classic-ui", r.srcInterface());
    }

    @Test
    void provisioningIsMinutePrecisionAndCarriesSource() {
        EventRow r = EventDeriver.provisioning(new EventDeriver.ProvSrc(
                "txn-1", "Add", "LCM", "Committed", "Success", T1, "9/1/26, 10:00 AM"), RUN);
        assertEquals(EventDeriver.TYPE_PROVISIONING_TXN, r.srcObjectType());
        assertEquals(EventType.PROVISIONING_TXN, r.eventType());
        assertEquals(T1, r.srcEventTs());
        assertEquals(EventRow.MINUTE, r.srcEventTsPrecision());
        assertEquals("classic-rest", r.srcInterface());
    }

    @Test
    void unparseableOrAbsentTimestampYieldsNullPrecision() {
        EventRow audit = EventDeriver.audit(new EventDeriver.AuditSrc(
                "aud-2", "ServerUp", "system", null, null, null), RUN);
        assertNull(audit.srcEventTs());
        assertNull(audit.srcEventTsPrecision());
    }

    @Test
    void changedSourceAttributeIsANewEvent() {
        EventRow before = EventDeriver.provisioning(new EventDeriver.ProvSrc(
                "txn-9", "Add", "LCM", "Committed", "Success", T1, "9/1/26, 10:00 AM"), RUN);
        EventRow after = EventDeriver.provisioning(new EventDeriver.ProvSrc(
                "txn-9", "Add", "LCM", "Failed", "Error", T1, "9/1/26, 10:00 AM"), RUN);
        assertNotEquals(before.eventId(), after.eventId());
        assertNotEquals(before.eventFingerprint(), after.eventFingerprint());
    }
}
