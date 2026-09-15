package com.keyforge.iiq.identityentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * Projects an already-derived {@link AccountEntitlementAssignment} into a
 * {@link IdentityEntitlementRow} for {@code kf_identity_entitlement}. Pure and DB-free.
 *
 * <p>Maps only what the existing derivation actually provides (identity, application,
 * entitlement, value, source_attribute, resolution). The authoritative provenance fields
 * (source/assigner/dates/aggregation_state/granted_by_role) are read from the model's
 * (currently always-null) accessors or left NULL — never invented. An assignment with no
 * identity cannot be an identity→entitlement edge and yields {@link Optional#empty()}.
 */
public final class IdentityEntitlementRowMapper {

    private IdentityEntitlementRowMapper() {
    }

    public static Optional<IdentityEntitlementRow> map(AccountEntitlementAssignment a) {
        String identityId = canonicalOrNull(a.getIdentityId());
        if (identityId == null) {
            return Optional.empty(); // not an identity→entitlement edge; never fabricate one
        }
        String id = deterministicId(a.getIdentityId(), a.getApplicationId(),
                a.getSourceAttribute(), a.getEntitlementValue());
        String resolutionStatus = a.getResolutionStatus() == null ? null : a.getResolutionStatus().name();

        return Optional.of(new IdentityEntitlementRow(
                id,
                identityId,
                blankToNull(a.getIdentityDisplayName()),
                canonicalOrNull(a.getApplicationId()),
                blankToNull(a.getApplicationName()),
                canonicalOrNull(a.getEntitlementId()),
                blankToNull(a.getEntitlementValue()),
                blankToNull(a.getEntitlementType()),
                blankToNull(a.getSourceAttribute()),
                resolutionStatus,
                // --- authoritative provenance: NOT provided by the current source -> NULL ---
                null,                                   // source (role-derived/direct/detected/requested)
                blankToNull(a.getAssignedBy()),         // assigner (model accessor is always null today)
                parseScimTimestamp(a.getAssignedDate()),// assigned_date (always null today)
                parseScimTimestamp(a.getExpirationDate()), // end_date (always null today)
                null,                                   // aggregation_state
                null));                                 // granted_by_role
    }

    static String deterministicId(String identityId, String applicationId, String sourceAttribute, String value) {
        String key = "IdentityEntitlement|" + nullToEmpty(identityId) + "|" + nullToEmpty(applicationId)
                + "|" + nullToEmpty(sourceAttribute) + "|" + nullToEmpty(value);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        try {
            if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
                String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                        + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
                return UUID.fromString(dashed).toString();
            }
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    static LocalDateTime parseScimTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException withOffset) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (DateTimeParseException withoutOffset) {
                return null;
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
