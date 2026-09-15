package com.keyforge.iiq.accountentitlement;

import com.keyforge.iiq.model.AccountEntitlementAssignment;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Projects an already-derived {@link AccountEntitlementAssignment} into a
 * {@link AccountEntitlementRow} for {@code kf_account_entitlement}. Pure and DB-free.
 * Reuses the existing derivation's output verbatim — it maps, never re-derives or invents.
 * The primary key is deterministic so re-runs update in place.
 */
public final class AccountEntitlementRowMapper {

    private AccountEntitlementRowMapper() {
    }

    public static AccountEntitlementRow map(AccountEntitlementAssignment a) {
        String id = deterministicId(a.getAccountId(), a.getApplicationId(),
                a.getSourceAttribute(), a.getEntitlementValue());
        String resolutionStatus = a.getResolutionStatus() == null ? null : a.getResolutionStatus().name();

        return new AccountEntitlementRow(
                id,
                canonicalOrNull(a.getAccountId()),
                blankToNull(a.getAccountName()),
                canonicalOrNull(a.getApplicationId()),
                blankToNull(a.getApplicationName()),
                canonicalOrNull(a.getEntitlementId()),
                blankToNull(a.getEntitlementValue()),
                blankToNull(a.getEntitlementType()),
                blankToNull(a.getSourceAttribute()),
                resolutionStatus);
    }

    static String deterministicId(String accountId, String applicationId, String sourceAttribute, String value) {
        String key = "AccountEntitlement|" + nullToEmpty(accountId) + "|" + nullToEmpty(applicationId)
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
