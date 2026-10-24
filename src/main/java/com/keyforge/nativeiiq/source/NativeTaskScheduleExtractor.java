package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeTaskScheduleMapper;
import com.keyforge.nativeiiq.model.NativeTaskScheduleExtractionResult;
import com.keyforge.nativeiiq.model.NativeTaskScheduleRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Date;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.TaskSchedule} (task cron schedules). Same flow as
 * the validated native extractors (project ids, load one at a time, map, decache). STRICTLY READ-ONLY:
 * only {@code search}/{@code getObjectById}/{@code decache}. Ordered by {@code name} for deterministic
 * pagination.
 */
public final class NativeTaskScheduleExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeTaskScheduleExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeTaskScheduleExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * IIQ ignores firstRow/resultLimit (and so cannot filter before paging) for TaskSchedule, so the
     * changed-after filter and the start/limit window are both applied here in code, filter first.
     */
    public NativeTaskScheduleExtractionResult extract(int start, int limit, Date modifiedAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("name", true);
        qo.addOrdering("id", true);
        return extract(qo, Math.max(0, start), limit, modifiedAfter);
    }

    private NativeTaskScheduleExtractionResult extract(QueryOptions qo, int start, int limit, Date modifiedAfter)
            throws GeneralException {
        NativeTaskScheduleExtractionResult result = new NativeTaskScheduleExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "TaskSchedule", Instant.now());

        int matched = 0;
        Iterator<Object[]> ids = context.search(TaskSchedule.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                if (limit > 0 && result.getTaskSchedules().size() >= limit) {
                    break;
                }
                String id = (String) ids.next()[0];
                TaskSchedule ts = context.getObjectById(TaskSchedule.class, id);
                if (ts == null) {
                    continue;
                }
                try {
                    // Live TaskSchedule objects have neither timestamp. Keep undated schedules conservatively so
                    // an unavailable timestamp can never make an actually changed schedule disappear.
                    boolean undated = ts.getModified() == null && ts.getCreated() == null;
                    if (!undated && !NativeIncrementalQuery.changedAfter(ts.getModified(), ts.getCreated(), modifiedAfter)) {
                        continue;
                    }
                    if (matched++ < start) {
                        continue;
                    }
                    NativeTaskScheduleRow row = NativeTaskScheduleMapper.map(
                            ts, config.getSourceSystem(), config.getExtractionRunId());
                    result.getTaskSchedules().add(row);
                } finally {
                    context.decache(ts);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
