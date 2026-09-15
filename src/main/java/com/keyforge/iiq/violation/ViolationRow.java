package com.keyforge.iiq.violation;

import java.time.LocalDateTime;

/**
 * A row of the project-owned {@code kf_violation} table (PolicyViolation → identity). Fields
 * come from the verified SCIM PolicyViolation schema. {@code mitigator} and {@code expiration_date}
 * are PDF-required but NOT exposed by SCIM, so they are always NULL here (never fabricated);
 * remediation/certification linkage is a Phase-4 cross-reference, out of scope now.
 */
public record ViolationRow(
        String violationid,
        String policyName,
        String constraintName,
        String status,
        String description,
        String ownerId,
        String ownerDisplayName,
        String identityId,
        String identityDisplayName,
        String mitigator,
        LocalDateTime expirationDate) {
}
