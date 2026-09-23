package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeProvisioningTxnExtractionResult;
import com.keyforge.nativeiiq.source.NativeProvisioningTxnExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) ProvisioningTransaction extraction service — transport-independent, read-only. */
public final class NativeProvisioningTxnExtractionService {

    private final String sourceSystem;

    public NativeProvisioningTxnExtractionService() {
        this("IdentityIQ");
    }

    public NativeProvisioningTxnExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeProvisioningTxnExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeProvisioningTxnExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeProvisioningTxnExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
