package com.keyforge.iiq.workgroup;

/**
 * Thrown when an IdentityIQ Workgroup cannot be mapped to a {@code kf_workgroup} row —
 * e.g. a missing or non-UUID workgroup id (the id is the primary key). A single bad
 * record is reported and skipped, never invented.
 */
public class WorkgroupMappingException extends RuntimeException {

    public WorkgroupMappingException(String message) {
        super(message);
    }
}
