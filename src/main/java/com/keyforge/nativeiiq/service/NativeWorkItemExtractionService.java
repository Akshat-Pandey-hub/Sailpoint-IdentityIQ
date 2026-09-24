package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeWorkItemExtractionResult;
import com.keyforge.nativeiiq.source.NativeWorkItemExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) WorkItem extraction service — transport-independent, read-only. */
public final class NativeWorkItemExtractionService {

    private final String sourceSystem;

    public NativeWorkItemExtractionService() {
        this("IdentityIQ");
    }

    public NativeWorkItemExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeWorkItemExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeWorkItemExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeWorkItemExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
