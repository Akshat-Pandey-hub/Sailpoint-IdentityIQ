package com.keyforge.iiq.provisioningtransaction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Maps a {@link ProvisioningTransaction} to a {@link ProvisioningTxnRow}. Pure and DB-free.
 *
 * <p>{@code txnid} ← canonicalised {@code id}; all descriptor fields preserved as supplied (blanked
 * to NULL only when genuinely empty). Display timestamps ({@code created}/{@code modified}/
 * {@code lastRetry}) are parsed <b>only</b> with the one verified pattern {@code "M/d/yy, h:mm a"}
 * (US locale); an off-pattern or blank value yields {@code null} (never a fabricated value) while the
 * raw {@code created} string is retained in {@code createdDisplay}. Nothing is inferred.
 */
public final class ProvisioningTxnRowMapper {

    /** Verified {@code created} format, e.g. "7/28/26, 12:47 AM". */
    static final DateTimeFormatter CREATED_FORMAT =
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US);

    private ProvisioningTxnRowMapper() {
    }

    public static ProvisioningTxnRow map(ProvisioningTransaction t) {
        return new ProvisioningTxnRow(
                toCanonicalUuid(t.id()),
                blankToNull(t.id()),
                blankToNull(t.name()),
                blankToNull(t.operation()),
                blankToNull(t.source()),
                blankToNull(t.status()),
                blankToNull(t.statusMessage()),
                blankToNull(t.type()),
                blankToNull(t.typeMessage()),
                blankToNull(t.integration()),
                blankToNull(t.identityName()),
                blankToNull(t.identityDisplayName()),
                blankToNull(t.applicationName()),
                blankToNull(t.nativeIdentity()),
                blankToNull(t.accountDisplayName()),
                blankToNull(t.created()),
                parseDisplay(t.created()),
                parseDisplay(t.modified()),
                parseDisplay(t.lastRetry()),
                blankToNull(t.ticketId()),
                t.retry(),
                t.retryCount(),
                t.timedOut(),
                t.forced(),
                t.forceable(),
                blankToNull(t.result()),
                blankToNull(t.accessRequestId()),
                blankToNull(t.certificationName()));
    }

    static LocalDateTime parseDisplay(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), CREATED_FORMAT);
        } catch (DateTimeParseException notTheVerifiedFormat) {
            return null;
        }
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ProvisioningTxnMappingException(
                    "ProvisioningTransaction id is missing; cannot form a kf_provisioning_txn key.");
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
            throw new ProvisioningTxnMappingException(
                    "ProvisioningTransaction id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
