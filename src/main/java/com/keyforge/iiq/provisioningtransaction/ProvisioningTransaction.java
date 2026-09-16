package com.keyforge.iiq.provisioningtransaction;

/**
 * A SailPoint IdentityIQ ProvisioningTransaction as returned by the classic REST endpoint
 * {@code GET /rest/provisioningTransactions} (verified live). Captures the transaction-level fields
 * that endpoint actually exposes (all confirmed present across the live set); the transaction's own
 * authoritative references are {@code accessRequestId} (→ Request) and {@code certificationName}
 * (→ Certification). Nested item arrays ({@code attributeRequests}/{@code permissionRequests}) are
 * empty on this instance, so no item-level data is modelled here (see kf_provisioning_item, deferred).
 * Nothing is inferred — a field IIQ leaves null stays null.
 *
 * <p>{@code created}/{@code modified}/{@code lastRetry} are the source's display strings
 * (e.g. {@code created} = "M/d/yy, h:mm a"); they are preserved verbatim and parsed best-effort by
 * the row mapper.
 */
public record ProvisioningTransaction(
        String id,
        String name,
        String operation,
        String source,
        String status,
        String statusMessage,
        String type,
        String typeMessage,
        String integration,
        String identityName,
        String identityDisplayName,
        String applicationName,
        String nativeIdentity,
        String accountDisplayName,
        String created,
        String modified,
        String lastRetry,
        String ticketId,
        Boolean retry,
        Integer retryCount,
        Boolean timedOut,
        Boolean forced,
        Boolean forceable,
        String result,
        String accessRequestId,
        String certificationName) {

    /**
     * Returns a copy with the two authoritative references replaced. The list endpoint leaves
     * {@code accessRequestId}/{@code certificationName} null; the per-transaction detail route
     * populates them, so the event-link derivation enriches them from detail before resolving.
     */
    public ProvisioningTransaction withReferences(String accessRequestId, String certificationName) {
        return new ProvisioningTransaction(id, name, operation, source, status, statusMessage, type,
                typeMessage, integration, identityName, identityDisplayName, applicationName, nativeIdentity,
                accountDisplayName, created, modified, lastRetry, ticketId, retry, retryCount, timedOut,
                forced, forceable, result, accessRequestId, certificationName);
    }
}
