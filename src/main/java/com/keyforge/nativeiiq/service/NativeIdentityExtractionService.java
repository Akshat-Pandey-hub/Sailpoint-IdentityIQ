package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeExtractionResult;
import com.keyforge.nativeiiq.source.NativeIdentityExtractor;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Native (Java-API) Identity extraction service — the transport-independent source operation. It runs
 * inside the IdentityIQ runtime, reads {@code sailpoint.object.Identity} through the live
 * {@link SailPointContext}, and returns a plain {@link NativeExtractionResult} of value objects
 * ({@code NativeIdentityRow}) with no SailPoint/Hibernate references crossing the boundary.
 *
 * <p>This is the clean service boundary the rest of the platform depends on: it knows nothing about
 * HTTP/REST/SCIM or PostgreSQL. How the result travels from IIQ to our engine (today: the plugin REST
 * resource) is a transport concern, kept out of here. STRICTLY READ-ONLY — it only searches, loads,
 * reads getters and decaches (see {@link NativeIdentityExtractor}); it never mutates IdentityIQ.
 */
public final class NativeIdentityExtractionService {

    /** Source-system label stamped on the lineage of each row. */
    private final String sourceSystem;

    public NativeIdentityExtractionService() {
        this("IdentityIQ");
    }

    public NativeIdentityExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty()) ? "IdentityIQ" : sourceSystem.trim();
    }

    /**
     * Extracts one deterministic, stable-ordered page of identities (rows {@code start}..{@code start+limit}).
     *
     * @param context the live IIQ context (supplied by the runtime; never obtained via HTTP)
     * @param start   zero-based offset of the first identity ({@code < 0} treated as 0)
     * @param limit   maximum identities to return ({@code <= 0} means no bound = full extraction)
     */
    public NativeExtractionResult extract(SailPointContext context, int start, int limit) throws GeneralException {
        NativeExtractionConfig config = NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        return new NativeIdentityExtractor(context, config).extract(Math.max(0, start), limit);
    }

    /** Extracts all identities in a single deterministic scan (no page bound). */
    public NativeExtractionResult extractAll(SailPointContext context) throws GeneralException {
        return extract(context, 0, 0);
    }
}
