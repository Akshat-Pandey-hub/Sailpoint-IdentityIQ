package com.keyforge.iiq.identityrole;

/** Thrown when an {@link IdentityRoleAssignment} cannot be mapped to a persistable row. */
public class IdentityRoleMappingException extends RuntimeException {

    public IdentityRoleMappingException(String message) {
        super(message);
    }
}
