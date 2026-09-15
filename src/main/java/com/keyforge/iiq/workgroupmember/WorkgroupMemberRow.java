package com.keyforge.iiq.workgroupmember;

/**
 * A row of the project-owned {@code kf_workgroup_member} migration table — the PDF's
 * workgroup N-to-N identity relationship. Every value comes from the authoritative
 * Edit-Workgroup members grid; nothing is inferred or fabricated.
 *
 * @param id           deterministic UUID of (workgroup_id|identity_id)
 * @param workgroupId  the workgroup id (canonical UUID)
 * @param identityId   the member identity id (canonical UUID)
 * @param memberName   member username (source {@code name})
 * @param firstName    source {@code firstname}
 * @param lastName     source {@code lastname}
 */
public record WorkgroupMemberRow(
        String id,
        String workgroupId,
        String identityId,
        String memberName,
        String firstName,
        String lastName) {
}
