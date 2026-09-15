package com.keyforge.iiq.eventlink;

/** Thrown when an event link cannot be mapped (e.g. a missing/non-UUID audit event id).
 * Reported and skipped, never invented. */
public class EventLinkMappingException extends RuntimeException {
    public EventLinkMappingException(String message) {
        super(message);
    }
}
