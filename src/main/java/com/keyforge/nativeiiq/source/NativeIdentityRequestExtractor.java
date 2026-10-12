package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeIdentityRequestMapper;
import com.keyforge.nativeiiq.model.NativeIdentityRequestExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityRequestRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.IdentityRequest;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.IdentityRequest}. Read-only: {@code countObjects}/
 * {@code search}/{@code getObjectById}/{@code decache}. Ordered by id for deterministic pagination; the
 * derived items and approval summaries ride along inside each request row.
 */
public final class NativeIdentityRequestExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeIdentityRequestExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeIdentityRequestExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * One page, optionally bounded to objects changed after {@code modifiedAfter} (server-side: modified after it,
     * or never modified and created after it; {@code null} = the full population, unchanged behaviour). The
     * source count uses the same bound, so the caller's complete-scan guard compares like with like.
     */
    public NativeIdentityRequestExtractionResult extract(int start, int limit, java.util.Date modifiedAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyModifiedAfter(qo, modifiedAfter);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeIdentityRequestExtractionResult result = new NativeIdentityRequestExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "IdentityRequest", Instant.now());
        result.setSourceCount(context.countObjects(IdentityRequest.class,
                NativeIncrementalQuery.applyModifiedAfter(new QueryOptions(), modifiedAfter)));

        Iterator<Object[]> ids = context.search(IdentityRequest.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            IdentityRequest r = context.getObjectById(IdentityRequest.class, id);
            if (r == null) {
                continue;
            }
            try {
                NativeIdentityRequestRow row = NativeIdentityRequestMapper.map(
                        r, config.getSourceSystem(), config.getExtractionRunId());
                result.getRows().add(row);
            } finally {
                context.decache(r);
            }
        }
        result.setFinishedAt(Instant.now());
        return result;
    }
}
