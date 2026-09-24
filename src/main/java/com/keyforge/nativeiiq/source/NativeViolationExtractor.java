package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeViolationMapper;
import com.keyforge.nativeiiq.model.NativeViolationExtractionResult;
import com.keyforge.nativeiiq.model.NativeViolationRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.PolicyViolation}. Same read-only flow as the
 * validated native extractors (project ids, load one at a time, map, decache). Ordered by {@code id}
 * for deterministic pagination.
 */
public final class NativeViolationExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeViolationExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeViolationExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo);
    }

    private NativeViolationExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeViolationExtractionResult result = new NativeViolationExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "PolicyViolation", Instant.now());

        Iterator<Object[]> ids = context.search(PolicyViolation.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            PolicyViolation v = context.getObjectById(PolicyViolation.class, id);
            if (v == null) {
                continue;
            }
            try {
                NativeViolationRow row = NativeViolationMapper.map(
                        v, config.getSourceSystem(), config.getExtractionRunId());
                result.getViolations().add(row);
            } finally {
                context.decache(v);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
