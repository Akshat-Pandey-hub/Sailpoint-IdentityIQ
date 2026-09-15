package com.keyforge.iiq.auditevent;

/** Thrown when an AuditEvent cannot be mapped (e.g. a missing/non-UUID id).
 * Reported and skipped, never invented. */
public class AuditEventMappingException extends RuntimeException {
    public AuditEventMappingException(String message) {
        super(message);
    }
}
