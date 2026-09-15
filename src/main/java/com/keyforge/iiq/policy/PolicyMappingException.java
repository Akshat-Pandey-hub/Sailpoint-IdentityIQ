package com.keyforge.iiq.policy;

/** Thrown when a Policy cannot be mapped to a {@code kf_policy} row (e.g. a missing/non-UUID id).
 * Reported and skipped, never invented. */
public class PolicyMappingException extends RuntimeException {
    public PolicyMappingException(String message) {
        super(message);
    }
}
