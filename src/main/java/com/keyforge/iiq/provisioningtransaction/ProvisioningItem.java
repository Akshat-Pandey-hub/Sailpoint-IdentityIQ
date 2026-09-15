package com.keyforge.iiq.provisioningtransaction;

/**
 * One line of a ProvisioningTransaction's provisioning plan — a provisioning <b>item</b> — as returned
 * by the IdentityIQ classic REST <b>detail</b> route {@code GET /rest/provisioningTransactions/{id}}
 * (verified live). The list route returns these arrays empty; only the per-transaction detail
 * populates them.
 *
 * <p>Items come from three arrays on the detail object, distinguished by {@link #requestType}:
 * {@code attributeRequests} ("attribute"), {@code permissionRequests} ("permission"), and
 * {@code filteredRequests} ("filtered"). Each observed item carries the same shape:
 * <pre>
 * { "operation":"Add|Set|Remove", "name":"posixgroups", "value":"cn=qa,...",
 *   "result":"committed|failed|null", "reason":null, "attributeRequest":false, "errorMessages":[] }
 * </pre>
 * The parent relationship is explicit and evidence-based: the item was read from that transaction's
 * own detail response, so {@link #parentTransactionId} is the authoritative foreign key (no name
 * inference). Extraction-only; nothing is written back.
 *
 * <p>Note: {@code permissionRequests} was present but empty across all 103 live transactions, so its
 * field projection is unverified on this instance; attribute and filtered requests are fully
 * evidenced (251 + 10 items).
 *
 * @param parentTransactionId the owning ProvisioningTransaction id (raw 32-char IIQ GUID)
 * @param requestType         which plan array this came from: {@code attribute|permission|filtered}
 * @param itemIndex           0-based position within that array (for a stable, deterministic id)
 * @param operation           plan operation: {@code Add}, {@code Set}, {@code Remove}
 * @param name                attribute/permission name
 * @param value               requested value (may be masked, e.g. password "********")
 * @param result              per-item result: {@code committed}, {@code failed}, or null
 * @param reason              filter/skip reason (e.g. "Does Not Exist"), or null
 * @param attributeRequest    the source {@code attributeRequest} boolean flag on the item
 * @param errorMessagesJson   the raw {@code errorMessages} JSON array preserved verbatim (or null)
 */
public record ProvisioningItem(
        String parentTransactionId,
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
