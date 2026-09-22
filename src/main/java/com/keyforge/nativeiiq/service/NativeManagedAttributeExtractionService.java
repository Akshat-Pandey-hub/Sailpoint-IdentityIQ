package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeManagedAttributeExtractionResult;
import com.keyforge.nativeiiq.source.NativeManagedAttributeExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) ManagedAttribute extraction service — the transport-independent source operation,
 * mirroring {@code NativeIdentityExtractionService}. Runs inside the IdentityIQ runtime, reads
 * {@code sailpoint.object.ManagedAttribute} through the live {@link SailPointContext}, and returns a
 * plain {@link NativeManagedAttributeExtractionResult} of value objects — no SailPoint/Hibernate
 * references cross the boundary. Knows nothing about HTTP/REST/SCIM or PostgreSQL. STRICTLY READ-ONLY.
 */
public final class NativeManagedAttributeExtractionService {

    private final String sourceSystem;

    public NativeManagedAttributeExtractionService() {
        this("IdentityIQ");
    }

    public NativeManagedAttributeExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    /**
     * Extracts one deterministic, stable-ordered page of managed attributes.
     *
     * @param context the live IIQ context (supplied by the runtime; never obtained via HTTP)
     * @param start   zero-based offset ({@code < 0} treated as 0)
     * @param limit   maximum rows ({@code <= 0} means no bound = full extraction)
     */
    public NativeManagedAttributeExtractionResult extract(SailPointContext context, int start, int limit)
            throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeManagedAttributeExtractor(context, config).extract(Math.max(0, start), limit);
    }

    /** Extracts all managed attributes in a single deterministic scan (no page bound). */
    public NativeManagedAttributeExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
