package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeHistRoleEventMapper;
import com.keyforge.nativeiiq.model.NativeHistRoleEventRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.accesshistory.HistoricalRoleEvent;
import sailpoint.tools.GeneralException;

import java.util.List;

/**
 * Native source adapter for {@code sailpoint.object.accesshistory.HistoricalRoleEvent} — immutable
 * Access-History events. Read-only. Receives the Access-History-bound {@link SailPointContext} from the
 * service.
 */
public final class NativeHistRoleEventExtractor {

    private final SailPointContext ahContext;
    private final NativeExtractionConfig config;

    public NativeHistRoleEventExtractor(SailPointContext ahContext, NativeExtractionConfig config) {
        this.ahContext = ahContext;
        this.config = config;
    }

    /** Appends one mapped row per source object in the requested page; returns the total source count. */
    public int extract(int start, int limit, List<NativeHistRoleEventRow> out) throws GeneralException {
        return NativeAccessHistoryScan.scan(
                ahContext, HistoricalRoleEvent.class, start, limit,
                o -> NativeHistRoleEventMapper.map(o, config.getSourceSystem(), config.getExtractionRunId()),
                out);
    }
}
