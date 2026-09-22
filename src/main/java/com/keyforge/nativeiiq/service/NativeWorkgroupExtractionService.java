package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeWorkgroupExtractionResult;
import com.keyforge.nativeiiq.source.NativeWorkgroupExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) Workgroup extraction service — the transport-independent source operation,
 * mirroring the validated native services. Runs inside the IIQ runtime, reads workgroup
 * {@code Identity} objects ({@code workgroup==true}) through the live {@link SailPointContext}, returns
 * plain value objects. Knows nothing about HTTP/REST/SCIM or PostgreSQL. STRICTLY READ-ONLY.
 */
public final class NativeWorkgroupExtractionService {

    private final String sourceSystem;

    public NativeWorkgroupExtractionService() {
        this("IdentityIQ");
    }

    public NativeWorkgroupExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeWorkgroupExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeWorkgroupExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeWorkgroupExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
