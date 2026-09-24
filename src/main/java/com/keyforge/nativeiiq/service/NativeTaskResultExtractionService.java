package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeTaskResultExtractionResult;
import com.keyforge.nativeiiq.source.NativeTaskResultExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) {@code TaskResult} extraction service — the transport-independent source operation,
 * mirroring the validated native services. Runs inside the IIQ runtime, reads
 * {@code sailpoint.object.TaskResult} through the live {@link SailPointContext}, returns plain value
 * objects. Knows nothing about HTTP/REST/SCIM or PostgreSQL. STRICTLY READ-ONLY.
 */
public final class NativeTaskResultExtractionService {

    private final String sourceSystem;

    public NativeTaskResultExtractionService() {
        this("IdentityIQ");
    }

    public NativeTaskResultExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeTaskResultExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeTaskResultExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeTaskResultExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
