package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeAccountEntitlementExtractionResult;
import com.keyforge.nativeiiq.source.NativeAccountEntitlementExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) account-entitlement extraction service — the transport-independent source operation
 * over {@code Link}s, mirroring the validated native services. Runs inside the IIQ runtime, returns plain
 * value objects, knows nothing about HTTP/REST/SCIM or PostgreSQL. STRICTLY READ-ONLY.
 */
public final class NativeAccountEntitlementExtractionService {

    private final String sourceSystem;

    public NativeAccountEntitlementExtractionService() {
        this("IdentityIQ");
    }

    public NativeAccountEntitlementExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeAccountEntitlementExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        return extract(context, start, limit, null);
    }

    /** One page, bounded to objects changed after {@code modifiedAfter} ({@code null} = full population). */
    public NativeAccountEntitlementExtractionResult extract(SailPointContext context, int start, int limit,
                      java.util.Date modifiedAfter) throws GeneralException {
        return extract(context, start, limit, modifiedAfter, true);
    }

    /** One page; counts the population only when {@code includeCount} is true ({@code -1} otherwise). */
    public NativeAccountEntitlementExtractionResult extract(SailPointContext context, int start, int limit,
                      java.util.Date modifiedAfter, boolean includeCount) throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeAccountEntitlementExtractor(context, config).extract(Math.max(0, start), limit, modifiedAfter,
                includeCount);
    }

    public NativeAccountEntitlementExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
