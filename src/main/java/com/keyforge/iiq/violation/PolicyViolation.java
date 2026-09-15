package com.keyforge.iiq.violation;

/**
 * A SailPoint IdentityIQ PolicyViolation (SCIM {@code PolicyViolation} resource). Verified
 * live schema fields: {@code id, policyName, constraintName, status, description, owner,
 * identity}. The PDF's mitigator/expiration/remediation-links are NOT in the SCIM schema and
 * are therefore not present here (represented as NULL downstream, never fabricated).
 */
public record PolicyViolation(
        String id,
        String policyName,
        String constraintName,
        String status,
        String description,
        Ref owner,
        Ref identity) {

    /** SCIM complex reference: {@code value} (id), {@code $ref}, {@code displayName}. */
    public record Ref(String value, String ref, String displayName) {
    }
}
