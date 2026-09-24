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
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("name", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo);
    }

    private NativeTaskScheduleExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeTaskScheduleExtractionResult result = new NativeTaskScheduleExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "TaskSchedule", Instant.now());

        Iterator<Object[]> ids = context.search(TaskSchedule.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            TaskSchedule ts = context.getObjectById(TaskSchedule.class, id);
            if (ts == null) {
                continue;
            }
            try {
                NativeTaskScheduleRow row = NativeTaskScheduleMapper.map(
                        ts, config.getSourceSystem(), config.getExtractionRunId());
                result.getTaskSchedules().add(row);
            } finally {
                context.decache(ts);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
