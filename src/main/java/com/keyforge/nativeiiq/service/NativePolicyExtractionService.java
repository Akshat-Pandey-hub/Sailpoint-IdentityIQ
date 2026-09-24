package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativePolicyExtractionResult;
import com.keyforge.nativeiiq.source.NativePolicyExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) {@code Policy} extraction service. STRICTLY READ-ONLY. */
public final class NativePolicyExtractionService {

    private final String sourceSystem;

    public NativePolicyExtractionService() {
        this("IdentityIQ");
    }

    public NativePolicyExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativePolicyExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativePolicyExtractor(context, config).extract(Math.max(0, start), limit);
    }
}
