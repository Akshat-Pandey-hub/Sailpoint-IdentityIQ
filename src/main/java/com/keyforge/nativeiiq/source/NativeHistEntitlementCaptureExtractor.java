package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeHistEntitlementCaptureMapper;
import com.keyforge.nativeiiq.model.NativeHistEntitlementCaptureRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.accesshistory.HistoricalEntitlementCapture;
import sailpoint.tools.GeneralException;

import java.util.List;

/**
 * Native source adapter for {@code sailpoint.object.accesshistory.HistoricalEntitlementCapture} — immutable
 * historical entitlement evidence. Read-only. Receives the Access-History-bound {@link SailPointContext}
 * (these objects live in {@code DatabaseInstance.ACCESS_HISTORY}); the service supplies it.
 */
public final class NativeHistEntitlementCaptureExtractor {

    private final SailPointContext ahContext;
    private final NativeExtractionConfig config;

    public NativeHistEntitlementCaptureExtractor(SailPointContext ahContext, NativeExtractionConfig config) {
        this.ahContext = ahContext;
        this.config = config;
    }

    /** Appends one mapped row per source object in the requested page; returns the total source count. */
    public int extract(int start, int limit, List<NativeHistEntitlementCaptureRow> out) throws GeneralException {
        return NativeAccessHistoryScan.scan(
                ahContext, HistoricalEntitlementCapture.class, start, limit,
                o -> NativeHistEntitlementCaptureMapper.map(o, config.getSourceSystem(), config.getExtractionRunId()),
                out);
    }
}
