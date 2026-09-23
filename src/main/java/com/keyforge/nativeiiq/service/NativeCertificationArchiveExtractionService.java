package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeCertificationArchiveExtractionResult;
import com.keyforge.nativeiiq.source.NativeCertificationArchiveExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) CertificationArchive extraction service — the transport-independent source operation,
 * mirroring the validated native services. Runs inside the IIQ runtime, reads
 * {@code sailpoint.object.CertificationArchive} through the live {@link SailPointContext}, returns plain
 * value objects. Knows nothing about HTTP/REST/SCIM or PostgreSQL. STRICTLY READ-ONLY.
 */
public final class NativeCertificationArchiveExtractionService {

    private final String sourceSystem;

    public NativeCertificationArchiveExtractionService() {
        this("IdentityIQ");
    }

    public NativeCertificationArchiveExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    public NativeCertificationArchiveExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeCertificationArchiveExtractor(context, config).extract(Math.max(0, start), limit);
    }

    public NativeCertificationArchiveExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
