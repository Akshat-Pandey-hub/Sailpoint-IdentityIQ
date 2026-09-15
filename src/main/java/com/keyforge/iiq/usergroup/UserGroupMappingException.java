package com.keyforge.iiq.usergroup;

/**
 * Thrown when an IdentityIQ user group cannot be safely mapped to the target
 * schema (e.g. a missing/invalid source id). Unchecked so a single bad record is
 * reported and skipped without a fabricated value ever being written.
 */
public class UserGroupMappingException extends RuntimeException {

    public UserGroupMappingException(String message) {
        super(message);
    }

    public UserGroupMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
