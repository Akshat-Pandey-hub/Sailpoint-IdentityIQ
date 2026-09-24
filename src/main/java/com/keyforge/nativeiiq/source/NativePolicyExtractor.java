package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativePolicyMapper;
import com.keyforge.nativeiiq.model.NativePolicyExtractionResult;
import com.keyforge.nativeiiq.model.NativePolicyRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Policy;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/** Native source adapter for {@code sailpoint.object.Policy}. Read-only; ordered by {@code name}. */
public final class NativePolicyExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativePolicyExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativePolicyExtractionResult extract(int start, int limit) throws GeneralException {
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

    private NativePolicyExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativePolicyExtractionResult result = new NativePolicyExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "Policy", Instant.now());

        Iterator<Object[]> ids = context.search(Policy.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Policy p = context.getObjectById(Policy.class, id);
            if (p == null) {
                continue;
            }
            try {
                NativePolicyRow row = NativePolicyMapper.map(p, config.getSourceSystem(), config.getExtractionRunId());
                result.getPolicies().add(row);
            } finally {
                context.decache(p);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
