package com.keyforge.iiq.violation;

import java.util.UUID;

/**
 * Maps a {@link PolicyViolation} to a {@link ViolationRow}. Pure and DB-free. Owner and identity
 * ids are canonicalised (stored as the referenced IIQ id; not FK-validated — same convention as
 * {@code kf_object_owner}). mitigator/expiration are NULL (not in the SCIM source).
 */
public final class ViolationRowMapper {

    private ViolationRowMapper() {
    }

    public static ViolationRow map(PolicyViolation v) {
        String violationid = toCanonicalUuid(v.id());
        String ownerId = v.owner() == null ? null : canonicalOrNull(v.owner().value());
        String ownerDisplay = v.owner() == null ? null : blankToNull(v.owner().displayName());
        String identityId = v.identity() == null ? null : canonicalOrNull(v.identity().value());
        String identityDisplay = v.identity() == null ? null : blankToNull(v.identity().displayName());

        return new ViolationRow(
                violationid,
                blankToNull(v.policyName()),
                blankToNull(v.constraintName()),
                blankToNull(v.status()),
                blankToNull(v.description()),
                ownerId,
                ownerDisplay,
                identityId,
                identityDisplay,
                null,   // mitigator — not exposed by SCIM
                null);  // expiration_date — not exposed by SCIM
    }

    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return toCanonicalUuid(rawId);
        } catch (ViolationMappingException notAUuid) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ViolationMappingException(
                    "IdentityIQ policy violation id is missing; cannot use as kf_violation.violationid (UUID).");
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return UUID.fromString(dashed).toString();
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            throw new ViolationMappingException(
                    "IdentityIQ policy violation id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
