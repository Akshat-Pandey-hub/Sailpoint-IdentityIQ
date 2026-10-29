package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeCertificationEntityExtractionResult;
import com.keyforge.nativeiiq.source.NativeCertificationEntityExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) CertificationEntity extraction service — transport-independent, read-only. */
public final class NativeCertificationEntityExtractionService {

    private final String sourceSystem;

    public NativeCertificationEntityExtractionService() {
        this("IdentityIQ");
    }

    public NativeCertificationEntityExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeCertificationEntityExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        return extract(context, start, limit, null);
    }

    /** One page, bounded to objects changed after {@code modifiedAfter} ({@code null} = full population). */
    public NativeCertificationEntityExtractionResult extract(SailPointContext context, int start, int limit,
                      java.util.Date modifiedAfter) throws GeneralException {
        return extract(context, start, limit, modifiedAfter, true);
    }

    /** One page; counts the population only when {@code includeCount} is true ({@code -1} otherwise). */
    public NativeCertificationEntityExtractionResult extract(SailPointContext context, int start, int limit,
                      java.util.Date modifiedAfter, boolean includeCount) throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeCertificationEntityExtractor(context, config).extract(Math.max(0, start), limit, modifiedAfter,
                includeCount);
    }

    public NativeCertificationEntityExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
