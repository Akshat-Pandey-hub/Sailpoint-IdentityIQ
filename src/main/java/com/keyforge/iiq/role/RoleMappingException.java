package com.keyforge.iiq.role;

/**
 * Thrown when an IdentityIQ Role cannot be mapped to a {@code kf_role} row —
 * e.g. a missing or non-UUID role id. Mirrors the per-vertical mapping exceptions
 * used elsewhere (a single bad record is reported and skipped, never invented).
 */
public class RoleMappingException extends RuntimeException {

    public RoleMappingException(String message) {
        super(message);
    }
}
