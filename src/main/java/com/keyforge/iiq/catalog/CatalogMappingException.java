package com.keyforge.iiq.catalog;

/**
 * Raised when an {@link com.keyforge.iiq.model.Entitlement} cannot be safely mapped
 * to a {@code catalog} row (e.g. its id is not a valid PostgreSQL UUID).
 */
public class CatalogMappingException extends RuntimeException {

    public CatalogMappingException(String message) {
        super(message);
    }
}
