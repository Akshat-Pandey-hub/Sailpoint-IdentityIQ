package com.keyforge.iiq.nativeevent;

/**
 * One row of the native derived {@code kf_event_link} table (the {@code iiq_native} EVENT zone). Unlike
 * the REST audit-target link, every native link is derived from an <b>explicit source id column</b> —
 * never from a parsed name, timestamp, ordering or similarity — so {@code linkStatus} is always
 * {@code EXPLICIT} and {@code targetObjectId} is the verbatim id the source record carried.
 *
 * @param linkId           deterministic UUID of (eventId | linkType | targetObjectType | targetObjectId)
 * @param eventId          the derived {@code kf_event.event_id} this link belongs to
 * @param srcObjectType    the event's source IIQ class (provenance)
 * @param srcObjectId      the native source record id that produced the event + link (provenance)
 * @param targetObjectType the referenced object kind (Identity / IdentityRequest / WorkItem)
 * @param targetObjectId   the EXPLICIT source id reference, verbatim (never invented or resolved by name)
 * @param linkType         the explicit relationship (e.g. APPROVAL_OF_REQUEST, PROVISION_OWNER)
 * @param linkStatus       always {@code EXPLICIT} — the link came from a real id column
 * @param extractionRunId  the run that derived this row
 */
public record NativeEventLinkRow(
        String linkId,
        String eventId,
        String srcObjectType,
        String srcObjectId,
        String targetObjectType,
        String targetObjectId,
        String linkType,
        String linkStatus,
        String extractionRunId) {

    public static final String EXPLICIT = "EXPLICIT";
}
