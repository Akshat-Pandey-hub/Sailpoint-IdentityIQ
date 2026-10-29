package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativePolicyConstraintExtractionResult;
import com.keyforge.nativeiiq.source.NativePolicyConstraintExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) Policy-constraint extraction service. STRICTLY READ-ONLY. */
public final class NativePolicyConstraintExtractionService {

    private final String sourceSystem;

    public NativePolicyConstraintExtractionService() {
        this("IdentityIQ");
    }

    public NativePolicyConstraintExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativePolicyConstraintExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        return extract(context, start, limit, null);
    }

    public NativePolicyConstraintExtractionResult extract(SailPointContext context, int start, int limit,
                      java.util.Date modifiedAfter) throws GeneralException {
        return extract(context, start, limit, modifiedAfter, true);
    }

    /** One page; counts the population only when {@code includeCount} is true ({@code -1} otherwise). */
    public NativePolicyConstraintExtractionResult extract(SailPointContext context, int start, int limit,
                      java.util.Date modifiedAfter, boolean includeCount) throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativePolicyConstraintExtractor(context, config).extract(Math.max(0, start), limit, modifiedAfter,
                includeCount);
    }
}
