package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeHistCertificationMapper;
import com.keyforge.nativeiiq.model.NativeHistCertificationRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.accesshistory.HistoricalCertification;
import sailpoint.tools.GeneralException;

import java.util.List;

/**
 * Native source adapter for {@code sailpoint.object.accesshistory.HistoricalCertification} — immutable
 * historical certification evidence. Read-only. Receives the Access-History-bound {@link SailPointContext}
 * from the service.
 */
public final class NativeHistCertificationExtractor {

    private final SailPointContext ahContext;
    private final NativeExtractionConfig config;

    public NativeHistCertificationExtractor(SailPointContext ahContext, NativeExtractionConfig config) {
        this.ahContext = ahContext;
        this.config = config;
    }

    /** Appends one mapped row per source object in the requested page; returns the total source count. */
    public int extract(int start, int limit, List<NativeHistCertificationRow> out) throws GeneralException {
        return NativeAccessHistoryScan.scan(
                ahContext, HistoricalCertification.class, start, limit,
                o -> NativeHistCertificationMapper.map(o, config.getSourceSystem(), config.getExtractionRunId()),
                out);
    }
}
