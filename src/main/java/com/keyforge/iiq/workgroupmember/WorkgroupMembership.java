package com.keyforge.iiq.workgroupmember;

/**
 * One Workgroup → Identity membership as returned by the IdentityIQ Edit-Workgroup
 * "Members" grid ({@code define/groups/workgroupMembersDataSource.json}). Only the fields
 * the source actually returns are carried: the member identity id and its name parts.
 *
 * @param workgroupId the owning workgroup's IdentityIQ id
 * @param identityId  the member identity's IdentityIQ id ({@code id})
 * @param memberName  the member's username ({@code name})
 * @param firstName   {@code firstname}
 * @param lastName    {@code lastname}
 */
public record WorkgroupMembership(
        String workgroupId,
        String identityId,
        String memberName,
        String firstName,
        String lastName) {
}
