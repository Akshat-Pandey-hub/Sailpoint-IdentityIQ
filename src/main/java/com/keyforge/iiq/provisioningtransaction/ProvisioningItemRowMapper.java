package com.keyforge.iiq.provisioningtransaction;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Maps a {@link ProvisioningItem} to a {@link ProvisioningItemRow}. Pure and DB-free.
 *
 * <p>{@code txnid} is the parent transaction id canonicalised to a UUID <b>identically</b> to
 * {@link ProvisioningTxnRowMapper} (so the foreign key matches {@code kf_provisioning_txn.txnid}).
 * {@code itemid} is a deterministic name-based UUID over {@code (txnid | requestType | index)} —
 * the source gives items no id, so this yields a stable key for idempotent upsert. Nothing is
 * invented; absent fields stay null.
 */
public final class ProvisioningItemRowMapper {

    private ProvisioningItemRowMapper() {
    }

    public static ProvisioningItemRow map(ProvisioningItem it) {
        String txnid = toCanonicalUuid(it.parentTransactionId());
        String seed = txnid + "|" + it.requestType() + "|" + it.itemIndex();
        String itemid = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        return new ProvisioningItemRow(
                itemid,
                txnid,
                blankToNull(it.parentTransactionId()),
                blankToNull(it.requestType()),
                it.itemIndex(),
                blankToNull(it.operation()),
                blankToNull(it.name()),
                blankToNull(it.value()),
                blankToNull(it.result()),
                blankToNull(it.reason()),
                it.attributeRequest(),
                blankToNull(it.errorMessagesJson()));
    }

    /** Canonicalises the IIQ id to a dashed UUID (mirrors {@link ProvisioningTxnRowMapper}). */
    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ProvisioningItemMappingException(
                    "ProvisioningTransaction id is missing; cannot form kf_provisioning_item.txnid (UUID).");
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
            throw new ProvisioningItemMappingException(
                    "ProvisioningTransaction id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
