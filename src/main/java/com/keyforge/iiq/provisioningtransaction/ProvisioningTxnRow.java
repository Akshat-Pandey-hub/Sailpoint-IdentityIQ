package com.keyforge.iiq.provisioningtransaction;

import java.time.LocalDateTime;

/**
 * A row of {@code kf_provisioning_txn} — the transaction-level normalized ProvisioningTransaction
 * entity (PDF §5.1). Every column is a real field from {@code /rest/provisioningTransactions}; raw
 * IIQ id is preserved in {@code sourceId}. {@code createdDisplay} keeps the source display string
 * verbatim; {@code createdAt}/{@code modifiedAt}/{@code lastRetry} are best-effort parses (naive,
 * minute precision, no timezone — same limitation as kf_audit_event). {@code accessRequestId} and
 * {@code certificationName} are the raw references consumed by the separate kf_event_link path.
 */
public record ProvisioningTxnRow(
        String txnid,
        String sourceId,
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
        String createdDisplay,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt,
        LocalDateTime lastRetry,
        String ticketId,
        Boolean retry,
        Integer retryCount,
        Boolean timedOut,
        Boolean forced,
        Boolean forceable,
        String result,
        String accessRequestId,
        String certificationName) {
}
