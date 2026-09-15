package com.keyforge.iiq.entitlement;

/**
 * Raised when an {@link com.keyforge.iiq.model.Entitlement} cannot be safely mapped
 * to an {@code entitlement} row (e.g. its IdentityIQ id is not a valid PostgreSQL
 * UUID). Such records are reported and skipped rather than silently altered.
 */
public class EntitlementMappingException extends RuntimeException {

    public EntitlementMappingException(String message) {
        super(message);
    }
}
