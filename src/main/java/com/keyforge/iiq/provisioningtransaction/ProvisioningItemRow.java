package com.keyforge.iiq.provisioningtransaction;

/**
 * A persistable {@code kf_provisioning_item} row. {@code itemid} is a deterministic UUID derived from
 * the parent transaction id + request type + index (items have no natural id in the source), so
 * re-runs upsert idempotently. {@code txnid} is the canonical UUID of the parent transaction —
 * the same value stored as {@code kf_provisioning_txn.txnid} — establishing the evidence-based
 * relationship; {@code sourceTxnId} preserves the raw parent id.
 */
public record ProvisioningItemRow(
        String itemid,
        String txnid,
        String sourceTxnId,
        String requestType,
        int itemIndex,
        String operation,
        String name,
        String value,
        String result,
        String reason,
        Boolean attributeRequest,
        String errorMessagesJson) {
}
