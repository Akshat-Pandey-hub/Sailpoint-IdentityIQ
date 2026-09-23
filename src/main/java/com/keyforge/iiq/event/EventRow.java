package com.keyforge.iiq.event;

import java.time.LocalDateTime;

/**
 * One append-only row of the CEC {@code kf_event} store. Immutable; never updated once written.
 *
 * @param eventId              deterministic UUID = {@code nameUUID(src_system|src_object_type|
 *                             src_object_id|event_fingerprint)} — encodes the PDF §7.3 dedup identity
 * @param srcSystem            logical source system (constant {@code IdentityIQ} this phase)
 * @param srcObjectType        the IIQ class the event concerns (e.g. sailpoint.object.AuditEvent)
 * @param srcObjectId          the source object's stable id
 * @param eventType            normalized {@link EventType}
 * @param eventFingerprint     canonical md5 over the event's business attributes
 * @param srcEventTs           event occurrence time, or null when the source has none/unparseable
 * @param srcEventTsPrecision  {@code "precise"} | {@code "minute"} | null — honest fidelity marker
 * @param extractionRunId      the run that derived this row ({@code RunLedger.currentRunId()})
 * @param srcInterface         which interface produced the underlying source (scim|ui-rest|classic-*)
 * @param rawRef               pointer to a RAW payload — always null until a RAW zone exists
 * @param eventDetailJson      small JSON of the real business attributes (never fabricated), or null
 */
public record EventRow(
        String eventId,
        String srcSystem,
        String srcObjectType,
        String srcObjectId,
        String eventType,
        String eventFingerprint,
        LocalDateTime srcEventTs,
        String srcEventTsPrecision,
        String extractionRunId,
        String srcInterface,
        String rawRef,
        String eventDetailJson) {

    /** Precision marker for a source timestamp that is a real instant (ISO / epoch). */
    public static final String PRECISE = "precise";
    /** Precision marker for a source timestamp that is minute-granular with no seconds/zone. */
    public static final String MINUTE = "minute";
    /** Source timestamp has second-level granularity. */
    public static final String SECOND = "second";
}
