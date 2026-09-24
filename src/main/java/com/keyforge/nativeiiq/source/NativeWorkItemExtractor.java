package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeWorkItemMapper;
import com.keyforge.nativeiiq.model.NativeWorkItemExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkItemRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for live {@code sailpoint.object.WorkItem}. Read-only: {@code countObjects}/
 * {@code search}/{@code getObjectById}/{@code decache}. Ordered by id for deterministic pagination.
 */
public final class NativeWorkItemExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeWorkItemExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeWorkItemExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeWorkItemExtractionResult result = new NativeWorkItemExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "WorkItem", Instant.now());
        result.setSourceCount(context.countObjects(WorkItem.class, new QueryOptions()));

        Iterator<Object[]> ids = context.search(WorkItem.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            WorkItem w = context.getObjectById(WorkItem.class, id);
            if (w == null) {
                continue;
            }
            try {
                NativeWorkItemRow row = NativeWorkItemMapper.map(
                        w, config.getSourceSystem(), config.getExtractionRunId());
                result.getRows().add(row);
            } finally {
                context.decache(w);
            }
        }
        result.setFinishedAt(Instant.now());
        return result;
    }
}
