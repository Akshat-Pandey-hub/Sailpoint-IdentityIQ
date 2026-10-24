package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeWorkItemArchiveMapper;
import com.keyforge.nativeiiq.model.NativeWorkItemArchiveExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkItemArchiveRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.WorkItemArchive} — the immutable CEC/history record of
 * completed work items. Same read-only flow as the validated native extractors (project ids, load one at a
 * time, map, decache). STRICTLY READ-ONLY: only {@code search}/{@code getObjectById}/{@code decache}.
 * Ordering by {@code id} for deterministic pagination (always present, unlike name/archived).
 */
public final class NativeWorkItemArchiveExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeWorkItemArchiveExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeWorkItemArchiveExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * One page, optionally bounded to objects created after {@code createdAfter} (server-side
     * {@code created >} filter; {@code null} = the full population, unchanged behaviour). The source count
     * uses the same bound, so the caller's complete-scan guard compares like with like.
     */
    public NativeWorkItemArchiveExtractionResult extract(int start, int limit, java.util.Date createdAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyCreatedAfter(qo, createdAfter);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo, createdAfter);
    }

    private NativeWorkItemArchiveExtractionResult extract(QueryOptions qo, java.util.Date createdAfter) throws GeneralException {
        NativeWorkItemArchiveExtractionResult result = new NativeWorkItemArchiveExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "WorkItemArchive", Instant.now());

        // Count independently of the requested page for full-scan reconciliation by the client.
        result.setSourceCount(context.countObjects(WorkItemArchive.class,
                NativeIncrementalQuery.applyCreatedAfter(new QueryOptions(), createdAfter)));

        Iterator<Object[]> ids = context.search(WorkItemArchive.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                WorkItemArchive a = context.getObjectById(WorkItemArchive.class, id);
                if (a == null) {
                    continue;
                }
                try {
                    NativeWorkItemArchiveRow row = NativeWorkItemArchiveMapper.map(
                            a, config.getSourceSystem(), config.getExtractionRunId());
                    result.getWorkItemArchives().add(row);
                } finally {
                    context.decache(a);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
