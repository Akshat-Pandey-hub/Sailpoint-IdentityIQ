package com.keyforge.iiq.application;

/**
 * Raised when an {@link com.keyforge.iiq.model.Application} cannot be safely mapped
 * to an {@code applicationinstance} row (e.g. its IdentityIQ id is not a valid
 * PostgreSQL UUID). Such records are reported and skipped rather than silently altered.
 */
public class ApplicationInstanceMappingException extends RuntimeException {

    public ApplicationInstanceMappingException(String message) {
        super(message);
    }
}
