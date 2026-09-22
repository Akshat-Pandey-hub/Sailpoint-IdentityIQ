package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeApplicationExtractionResult;
import com.keyforge.nativeiiq.source.NativeApplicationExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) Application extraction service — the transport-independent source operation,
 * mirroring the validated Identity/ManagedAttribute services. Runs inside the IIQ runtime, reads
 * {@code sailpoint.object.Application} through the live {@link SailPointContext}, and returns plain
 * value objects. Knows nothing about HTTP/REST/SCIM or PostgreSQL. STRICTLY READ-ONLY.
 */
public final class NativeApplicationExtractionService {

    private final String sourceSystem;

    public NativeApplicationExtractionService() {
        this("IdentityIQ");
    }

    public NativeApplicationExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeApplicationExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeApplicationExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeApplicationExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
