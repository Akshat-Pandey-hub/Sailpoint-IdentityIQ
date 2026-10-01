package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeHistIdentityEventMapper;
import com.keyforge.nativeiiq.model.NativeHistIdentityEventRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.accesshistory.HistoricalIdentityEvent;
import sailpoint.tools.GeneralException;

import java.util.List;

/**
 * Native source adapter for {@code sailpoint.object.accesshistory.HistoricalIdentityEvent} — immutable
 * Access-History events. Read-only. Receives the Access-History-bound {@link SailPointContext} from the
 * service.
 */
public final class NativeHistIdentityEventExtractor {

    private final SailPointContext ahContext;
    private final NativeExtractionConfig config;

    public NativeHistIdentityEventExtractor(SailPointContext ahContext, NativeExtractionConfig config) {
        this.ahContext = ahContext;
        this.config = config;
    }

    /** Appends one mapped row per source object in the requested page; returns the total source count. */
    public int extract(int start, int limit, List<NativeHistIdentityEventRow> out) throws GeneralException {
        return NativeAccessHistoryScan.scan(
                ahContext, HistoricalIdentityEvent.class, start, limit,
                o -> NativeHistIdentityEventMapper.map(o, config.getSourceSystem(), config.getExtractionRunId()),
                out);
    }
}
