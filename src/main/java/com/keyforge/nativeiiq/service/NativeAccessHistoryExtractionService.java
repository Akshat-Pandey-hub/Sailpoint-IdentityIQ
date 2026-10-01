package com.keyforge.nativeiiq.service;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.model.NativeAccessHistoryExtractionResult;
import com.keyforge.nativeiiq.model.NativeAccessHistoryPage;
import com.keyforge.nativeiiq.model.NativeHistCertificationRow;
import com.keyforge.nativeiiq.model.NativeHistEntitlementCaptureRow;
import com.keyforge.nativeiiq.model.NativeHistIdentityEventRow;
import com.keyforge.nativeiiq.source.NativeHistCertificationExtractor;
import com.keyforge.nativeiiq.source.NativeHistEntitlementCaptureExtractor;
import com.keyforge.nativeiiq.source.NativeHistIdentityEventExtractor;

import sailpoint.api.DatabaseInstance;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Native (Java-API) Access-History extraction service. STRICTLY READ-ONLY. Extracts the three immutable
 * Access-History object types — {@code HistoricalEntitlementCapture}, {@code HistoricalIdentityEvent},
 * {@code HistoricalCertification} — in one run under one {@code extraction_run_id}.
 *
 * <p><b>Context:</b> Access-History objects do NOT live in the main IIQ datasource; they are stored in the
 * dedicated {@link DatabaseInstance#ACCESS_HISTORY} database with its own Hibernate SessionFactory. The
 * default runtime context cannot query them ({@code countObjects}/{@code getObjects} throw). This service
 * therefore obtains a context bound to that database via {@code SailPointFactory.createContext(ACCESS_HISTORY)}
 * — the exact supported mechanism IIQ's own {@code HistoryEventConsumer} uses — and releases it when done.
 * The live runtime {@link SailPointContext} must exist (the service runs inside the IIQ runtime), so the AH
 * Environment is initialized; this is not a standalone process.
 */
public final class NativeAccessHistoryExtractionService {

    private final String sourceSystem;

    public NativeAccessHistoryExtractionService() {
        this("IdentityIQ");
    }

    public NativeAccessHistoryExtractionService(String sourceSystem) {
        this.sourceSystem = (sourceSystem == null || sourceSystem.trim().isEmpty())
                ? "IdentityIQ" : sourceSystem.trim();
    }

    /**
     * Runs the extraction. {@code runtimeContext} is the live IIQ context supplied by the caller (task); it
     * establishes that we are inside the IIQ runtime. The Access-History objects themselves are read through
     * a separate ACCESS_HISTORY-bound context created internally. {@code start}/{@code limit} page each type
     * (limit &le; 0 = no limit / all rows).
     */
    public NativeAccessHistoryExtractionResult extract(SailPointContext runtimeContext, int start, int limit)
            throws GeneralException {
        if (runtimeContext == null) {
            throw new GeneralException(
                    "Native Access-History extraction requires the live IdentityIQ runtime SailPointContext");
        }
        NativeExtractionConfig config =
                NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        NativeAccessHistoryExtractionResult result = new NativeAccessHistoryExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), Instant.now());

        int safeStart = Math.max(0, start);

        AhContext ah = AhContext.acquire();
        try {
            result.setEntitlementCaptureSourceCount(
                    new NativeHistEntitlementCaptureExtractor(ah.context, config)
                            .extract(safeStart, limit, result.getEntitlementCaptures()));
            result.setIdentityEventSourceCount(
                    new NativeHistIdentityEventExtractor(ah.context, config)
                            .extract(safeStart, limit, result.getIdentityEvents()));
            result.setCertificationSourceCount(
                    new NativeHistCertificationExtractor(ah.context, config)
                            .extract(safeStart, limit, result.getCertifications()));
        } finally {
            ah.release();
        }

        result.setFinishedAt(Instant.now());
        return result;
    }

    // ---- Per-type paged extraction (one type per plugin endpoint; transport-facing) ----

    /** One page of {@code HistoricalEntitlementCapture} rows + the live source count. */
    public NativeAccessHistoryPage<NativeHistEntitlementCaptureRow> extractEntitlementCaptures(
            SailPointContext runtimeContext, int start, int limit) throws GeneralException {
        return runOne(runtimeContext,
                (ah, cfg, out) -> new NativeHistEntitlementCaptureExtractor(ah, cfg).extract(start, limit, out));
    }

    /** One page of {@code HistoricalIdentityEvent} rows + the live source count. */
    public NativeAccessHistoryPage<NativeHistIdentityEventRow> extractIdentityEvents(
            SailPointContext runtimeContext, int start, int limit) throws GeneralException {
        return runOne(runtimeContext,
                (ah, cfg, out) -> new NativeHistIdentityEventExtractor(ah, cfg).extract(start, limit, out));
    }

    /** One page of {@code HistoricalCertification} rows + the live source count. */
    public NativeAccessHistoryPage<NativeHistCertificationRow> extractCertifications(
            SailPointContext runtimeContext, int start, int limit) throws GeneralException {
        return runOne(runtimeContext,
                (ah, cfg, out) -> new NativeHistCertificationExtractor(ah, cfg).extract(start, limit, out));
    }

    /** Runs one extractor against a freshly-opened ACCESS_HISTORY context, releasing it afterwards. */
    private <R> NativeAccessHistoryPage<R> runOne(SailPointContext runtimeContext, PagedWork<R> work)
            throws GeneralException {
        if (runtimeContext == null) {
            throw new GeneralException(
                    "Native Access-History extraction requires the live IdentityIQ runtime SailPointContext");
        }
        NativeExtractionConfig config =
                NativeExtractionConfig.of(sourceSystem, null, Integer.valueOf(0), "native", null);
        List<R> rows = new ArrayList<R>();
        AhContext ah = AhContext.acquire();
        int sourceCount;
        try {
            sourceCount = work.run(ah.context, config, rows);
        } finally {
            ah.release();
        }
        return new NativeAccessHistoryPage<R>(
                config.getSourceSystem(), config.getExtractionRunId(), sourceCount, rows);
    }

    /** One extractor invocation against the ACCESS_HISTORY context; returns the source count. */
    private interface PagedWork<R> {
        int run(SailPointContext ah, NativeExtractionConfig config, List<R> out) throws GeneralException;
    }

    /**
     * The {@code DatabaseInstance.ACCESS_HISTORY} context for this thread, plus whether THIS call created it.
     * <p>Inside a plugin REST request (the {@code *-db} transport path) the IIQ framework has ALREADY put an
     * ACCESS_HISTORY context on the thread — exactly as it puts the main context that every other native
     * extractor uses via {@code getContext()}. So we must <b>fetch</b> that existing one with
     * {@code getCurrentContext(ACCESS_HISTORY)} and NOT release it (the framework owns its lifecycle);
     * calling {@code createContext} there throws {@code "Context already created for this thread!"}. Only when
     * the thread has none (a standalone/TaskExecutor run) do we create one and take responsibility for
     * releasing it. This mirrors how the 36 other entities simply use the context the framework provides.
     */
    private static final class AhContext {
        final SailPointContext context;
        private final boolean owned;

        private AhContext(SailPointContext context, boolean owned) {
            this.context = context;
            this.owned = owned;
        }

        static AhContext acquire() throws GeneralException {
            try {
                return new AhContext(SailPointFactory.getCurrentContext(DatabaseInstance.ACCESS_HISTORY), false);
            } catch (GeneralException noneOnThread) {
                return new AhContext(SailPointFactory.createContext(DatabaseInstance.ACCESS_HISTORY), true);
            }
        }

        void release() throws GeneralException {
            if (owned) {
                SailPointFactory.releaseContext(context);
            }
        }
    }
}
