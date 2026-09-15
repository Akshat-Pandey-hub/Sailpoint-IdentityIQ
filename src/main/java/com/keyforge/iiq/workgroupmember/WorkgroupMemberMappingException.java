package com.keyforge.iiq.workgroupmember;

/**
 * Thrown when a workgroup membership cannot be mapped to a {@code kf_workgroup_member} row —
 * e.g. a missing/non-UUID workgroup or identity id (both are part of the key). A single bad
 * record is reported and skipped, never invented.
 */
public class WorkgroupMemberMappingException extends RuntimeException {

    public WorkgroupMemberMappingException(String message) {
        super(message);
    }
}
