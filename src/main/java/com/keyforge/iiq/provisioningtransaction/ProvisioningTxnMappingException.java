package com.keyforge.iiq.provisioningtransaction;

/** Thrown when a ProvisioningTransaction cannot be mapped to a kf_provisioning_txn row
 * (e.g. a missing/non-UUID id). Reported and skipped, never invented. */
public class ProvisioningTxnMappingException extends RuntimeException {
    public ProvisioningTxnMappingException(String message) {
        super(message);
    }
}
