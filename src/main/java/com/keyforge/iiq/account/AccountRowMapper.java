package com.keyforge.iiq.account;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Account;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

/**
 * Maps an IdentityIQ {@link Account} to an {@link AccountRow}. Pure and DB-free. Core
 * fields become typed columns; the Account→Identity and Account→Application
 * relationships become resolved FK columns. The application-specific connector
 * attribute bag is preserved verbatim as JSON (arrays/objects intact). Nothing invented.
 */
public final class AccountRowMapper {

    private AccountRowMapper() {
    }

    public static AccountRow map(Account account,
                                 Set<String> existingUserIds,
                                 Set<String> existingInstanceIds) {
        String accountid = toCanonicalUuid(account.getId());
        String userid = resolveRef(account.getIdentity(), existingUserIds);
        String instanceid = resolveRef(account.getApplication(), existingInstanceIds);

        String identityDisplayName = account.getIdentity() == null
                ? null : blankToNull(account.getIdentity().getDisplayName());
        String applicationDisplayName = account.getApplication() == null
                ? null : blankToNull(account.getApplication().getDisplayName());

        Account.Meta meta = account.getMeta();
        LocalDateTime created = meta == null ? null : parseScimTimestamp(meta.getCreated());
        LocalDateTime lastModified = meta == null ? null : parseScimTimestamp(meta.getLastModified());

        return new AccountRow(
                accountid,
                userid,
                identityDisplayName,
                instanceid,
                applicationDisplayName,
                blankToNull(account.getNativeIdentity()),
                blankToNull(account.getDisplayName()),
                account.getActive(),
                account.getLocked(),
                account.getHasEntitlements(),
                account.getManuallyCorrelated(),
                parseScimTimestamp(account.getLastRefresh()),
                created,
                lastModified,
                connectorAttributesJson(account));
    }

    /**
     * The application-specific connector attribute bag as JSON (mail, groups[],
     * proxyAddresses[], …), or null. Preserved verbatim — arrays stay complete arrays,
     * nested objects keep their structure. This is genuinely-unmapped data with no fixed
     * columns; it is NOT a copy of the mapped core/relationship columns.
     */
    static String connectorAttributesJson(Account account) {
        ObjectNode attrs = account.getAdditionalAttributes();
        return attrs == null || attrs.isEmpty() ? null : attrs.toString();
    }

    public static String resolveRef(Account.Ref ref, Set<String> existingIds) {
        if (ref == null || ref.getValue() == null || ref.getValue().isBlank()) {
            return null;
        }
        String canonical;
        try {
            canonical = toCanonicalUuid(ref.getValue());
        } catch (AccountMappingException notAUuid) {
            return null;
        }
        return existingIds != null && existingIds.contains(canonical) ? canonical : null;
    }

    public static boolean hasIdentityRef(Account account) {
        return hasRefValue(account.getIdentity());
    }

    public static boolean hasApplicationRef(Account account) {
        return hasRefValue(account.getApplication());
    }

    private static boolean hasRefValue(Account.Ref ref) {
        return ref != null && ref.getValue() != null && !ref.getValue().isBlank();
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new AccountMappingException(
                    "IdentityIQ account id is missing; cannot use as account.accountid (UUID).");
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
            throw new AccountMappingException(
                    "IdentityIQ account id '" + rawId + "' is not a valid PostgreSQL UUID.");
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
}
