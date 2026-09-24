package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeIdentityRoleExtractionResult;
import com.keyforge.nativeiiq.source.NativeIdentityRoleExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) identity-role extraction service — transport-independent, read-only. */
public final class NativeIdentityRoleExtractionService {

    private final String sourceSystem;

    public NativeIdentityRoleExtractionService() {
        this("IdentityIQ");
    }

    public NativeIdentityRoleExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeIdentityRoleExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeIdentityRoleExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeIdentityRoleExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
