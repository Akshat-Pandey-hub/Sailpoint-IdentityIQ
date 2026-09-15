package com.keyforge.iiq.roleentitlement;

/**
 * Thrown when a role→entitlement grant cannot be mapped to a {@code kf_role_entitlement}
 * row — e.g. a missing/non-UUID role id (part of the key). A single bad record is reported
 * and skipped, never invented.
 */
public class RoleEntitlementMappingException extends RuntimeException {

    public RoleEntitlementMappingException(String message) {
        super(message);
    }
}
