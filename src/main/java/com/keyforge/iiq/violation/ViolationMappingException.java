package com.keyforge.iiq.violation;

/** Thrown when a PolicyViolation cannot be mapped to a {@code kf_violation} row (e.g. a
 * missing/non-UUID id). Reported and skipped, never invented. */
public class ViolationMappingException extends RuntimeException {
    public ViolationMappingException(String message) {
        super(message);
    }
}
