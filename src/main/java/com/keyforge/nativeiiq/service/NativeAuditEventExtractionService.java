package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeAuditEventExtractionResult;
import com.keyforge.nativeiiq.source.NativeAuditEventExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/** Native (Java-API) {@code AuditEvent} extraction service. STRICTLY READ-ONLY. */
public final class NativeAuditEventExtractionService {

    private final String sourceSystem;

    public NativeAuditEventExtractionService() {
        this("IdentityIQ");
    }

    public NativeAuditEventExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeAuditEventExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeAuditEventExtractor(context, config).extract(Math.max(0, start), limit);
    }
}
