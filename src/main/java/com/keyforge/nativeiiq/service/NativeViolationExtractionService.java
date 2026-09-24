package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeViolationExtractionResult;
import com.keyforge.nativeiiq.source.NativeViolationExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) {@code PolicyViolation} extraction service. STRICTLY READ-ONLY. */
public final class NativeViolationExtractionService {

    private final String sourceSystem;

    public NativeViolationExtractionService() {
        this("IdentityIQ");
    }

    public NativeViolationExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeViolationExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeViolationExtractor(context, config).extract(Math.max(0, start), limit);
    }
}
