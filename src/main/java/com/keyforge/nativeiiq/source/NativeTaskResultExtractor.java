package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeTaskResultMapper;
import com.keyforge.nativeiiq.model.NativeTaskResultExtractionResult;
import com.keyforge.nativeiiq.model.NativeTaskResultRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.TaskResult} (task/aggregation run history). Same
 * flow as the validated native extractors (project ids, load one at a time, map, decache). STRICTLY
 * READ-ONLY: only {@code search}/{@code getObjectById}/{@code decache}. Ordered by {@code created} then
 * {@code id} for deterministic pagination across a large, append-heavy population.
 */
public final class NativeTaskResultExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeTaskResultExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeTaskResultExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("created", true);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo);
    }

    private NativeTaskResultExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeTaskResultExtractionResult result = new NativeTaskResultExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "TaskResult", Instant.now());

        Iterator<Object[]> ids = context.search(TaskResult.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            TaskResult tr = context.getObjectById(TaskResult.class, id);
            if (tr == null) {
                continue;
            }
            try {
                NativeTaskResultRow row = NativeTaskResultMapper.map(
                        tr, config.getSourceSystem(), config.getExtractionRunId());
                result.getTaskResults().add(row);
            } finally {
                context.decache(tr);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
