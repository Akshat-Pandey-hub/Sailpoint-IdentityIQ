package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeLinkExtractionResult;
import com.keyforge.nativeiiq.source.NativeLinkExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) Link (account) extraction service — the transport-independent source operation,
 * mirroring the validated Identity/ManagedAttribute/Application services. Runs inside the IIQ runtime,
 * reads {@code sailpoint.object.Link} through the live {@link SailPointContext}, returns plain value
 * objects. Knows nothing about HTTP/REST/SCIM or PostgreSQL. STRICTLY READ-ONLY.
 */
public final class NativeLinkExtractionService {

    private final String sourceSystem;

    public NativeLinkExtractionService() {
        this("IdentityIQ");
    }

    public NativeLinkExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeLinkExtractionResult extract(SailPointContext context, int start, int limit) throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeLinkExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeLinkExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
