package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeSyslogEventMapper;
import com.keyforge.nativeiiq.model.NativeSyslogEventExtractionResult;
import com.keyforge.nativeiiq.model.NativeSyslogEventRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.SyslogEvent;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.SyslogEvent} — immutable operational log records.
 * Read-only ({@code search}/{@code getObjectById}/{@code decache}); emits {@code countObjects} for the
 * complete-scan guard. Ordered by {@code created} then {@code id} for deterministic pagination.
 */
public final class NativeSyslogEventExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeSyslogEventExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeSyslogEventExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * One page, optionally bounded to objects created after {@code createdAfter} (server-side
     * {@code created >} filter; {@code null} = the full population, unchanged behaviour). The source count
     * uses the same bound, so the caller's complete-scan guard compares like with like.
     */
    public NativeSyslogEventExtractionResult extract(int start, int limit, java.util.Date createdAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyCreatedAfter(qo, createdAfter);
        qo.addOrdering("created", true);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo, createdAfter);
    }

    private NativeSyslogEventExtractionResult extract(QueryOptions qo, java.util.Date createdAfter) throws GeneralException {
        NativeSyslogEventExtractionResult result = new NativeSyslogEventExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "SyslogEvent", Instant.now());

        result.setSourceCount(context.countObjects(SyslogEvent.class,
                NativeIncrementalQuery.applyCreatedAfter(new QueryOptions(), createdAfter)));

        Iterator<Object[]> ids = context.search(SyslogEvent.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                SyslogEvent s = context.getObjectById(SyslogEvent.class, id);
                if (s == null) {
                    continue;
                }
                try {
                    NativeSyslogEventRow row = NativeSyslogEventMapper.map(
                            s, config.getSourceSystem(), config.getExtractionRunId());
                    result.getSyslogEvents().add(row);
                } finally {
                    context.decache(s);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
