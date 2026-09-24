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
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeIdentityRequestExtractionResult result = new NativeIdentityRequestExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "IdentityRequest", Instant.now());
        result.setSourceCount(context.countObjects(IdentityRequest.class, new QueryOptions()));

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
