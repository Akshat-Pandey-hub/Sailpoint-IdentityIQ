package com.keyforge.iiq.provisioningtransaction;

/** Thrown when a provisioning event link cannot be mapped (e.g. a missing/non-UUID transaction id).
 * Reported and skipped, never invented. */
public class ProvisioningEventLinkMappingException extends RuntimeException {
    public ProvisioningEventLinkMappingException(String message) {
        super(message);
    }
}
