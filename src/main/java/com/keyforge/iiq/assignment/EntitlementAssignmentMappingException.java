package com.keyforge.iiq.assignment;

/**
 * Raised when a derived {@link com.keyforge.iiq.model.AccountEntitlementAssignment}
 * value cannot be safely converted for persistence (e.g. an id that is not a valid
 * PostgreSQL UUID). Such values are handled by leaving the relational column NULL and
 * preserving the raw value in {@code customattributes}, not by fabricating data.
 */
public class EntitlementAssignmentMappingException extends RuntimeException {

    public EntitlementAssignmentMappingException(String message) {
        super(message);
    }
}
