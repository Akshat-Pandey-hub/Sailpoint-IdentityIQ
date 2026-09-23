package com.keyforge.iiq.event;

/**
 * Normalized CEC event kinds emitted into {@code kf_event}. These are the only event types this phase
 * produces; no IIQ action taxonomy is invented (the raw AuditEvent {@code action} is preserved in
 * {@code event_detail} instead).
 */
public final class EventType {

    /** IdentityRequest approval interaction that has been decided (complete_date present). */
    public static final String APPROVAL_DECISION = "APPROVAL_DECISION";
    /** IdentityRequest approval interaction still open (no complete_date yet). */
    public static final String APPROVAL_OPEN = "APPROVAL_OPEN";
    /** TaskResult that has completed. */
    public static final String TASK_COMPLETED = "TASK_COMPLETED";
    /** TaskResult that has launched but not completed. */
    public static final String TASK_LAUNCHED = "TASK_LAUNCHED";
    /** A single AuditEvent. */
    public static final String AUDIT_EVENT = "AUDIT_EVENT";
    /** A provisioning transaction. */
    public static final String PROVISIONING_TXN = "PROVISIONING_TXN";
    /** A work item that has been archived (one event per WorkItemArchive, never one per sign-off). */
    public static final String WORKITEM_ARCHIVED = "WORKITEM_ARCHIVED";

    private EventType() {
    }
}
