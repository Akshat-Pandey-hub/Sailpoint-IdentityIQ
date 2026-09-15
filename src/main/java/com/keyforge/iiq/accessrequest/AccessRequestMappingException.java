package com.keyforge.iiq.accessrequest;

/** Thrown when an Access Request / item cannot be mapped (e.g. a missing/non-UUID id).
 * Reported and skipped, never invented. */
public class AccessRequestMappingException extends RuntimeException {
    public AccessRequestMappingException(String message) {
        super(message);
    }
}
