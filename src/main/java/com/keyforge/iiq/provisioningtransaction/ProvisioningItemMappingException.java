package com.keyforge.iiq.provisioningtransaction;

/** Thrown when a {@link ProvisioningItem} cannot be mapped to a persistable row. */
public class ProvisioningItemMappingException extends RuntimeException {

    public ProvisioningItemMappingException(String message) {
        super(message);
    }
}
