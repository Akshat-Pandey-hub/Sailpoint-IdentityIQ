package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeCertificationItemExtractionResult;
import com.keyforge.nativeiiq.source.NativeCertificationItemExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) CertificationItem extraction service — transport-independent, read-only. */
public final class NativeCertificationItemExtractionService {

    private final String sourceSystem;

    public NativeCertificationItemExtractionService() {
        this("IdentityIQ");
    }

    public NativeCertificationItemExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeCertificationItemExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeCertificationItemExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeCertificationItemExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
