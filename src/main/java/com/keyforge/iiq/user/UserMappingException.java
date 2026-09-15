package com.keyforge.iiq.user;

/**
 * Raised when an {@link com.keyforge.iiq.model.Identity} cannot be safely mapped
 * to a {@code usr} row (e.g. its IdentityIQ id is not a valid PostgreSQL UUID).
 * Such records are reported and skipped rather than being silently altered.
 */
public class UserMappingException extends RuntimeException {

    public UserMappingException(String message) {
        super(message);
    }
}
