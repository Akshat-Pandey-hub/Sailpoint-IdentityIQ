package com.keyforge.iiq.account;

/**
 * Raised when an {@link com.keyforge.iiq.model.Account} cannot be safely mapped to
 * an {@code account} row (e.g. its IdentityIQ id is not a valid PostgreSQL UUID).
 * Such records are reported and skipped rather than silently altered.
 */
public class AccountMappingException extends RuntimeException {

    public AccountMappingException(String message) {
        super(message);
    }
}
