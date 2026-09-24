package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativePolicyConstraintMapper;
import com.keyforge.nativeiiq.model.NativePolicyConstraintExtractionResult;

import sailpoint.api.SailPointContext;
import sailpoint.object.Policy;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for Policy constraints. Pages over the parent {@code Policy} (the unit the
 * constraints are reached through): projects Policy ids, loads each, expands its constraints, decaches.
 * Emits the total Policy count ({@code countObjects}) + the number of policies this page covered so the
 * client can guard a complete scan. STRICTLY READ-ONLY.
 */
public final class NativePolicyConstraintExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativePolicyConstraintExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativePolicyConstraintExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }

        NativePolicyConstraintExtractionResult result = new NativePolicyConstraintExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "PolicyConstraint", Instant.now());
        result.setSourceCount(context.countObjects(Policy.class, new QueryOptions()));

        int policies = 0;
        Iterator<Object[]> ids = context.search(Policy.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Policy p = context.getObjectById(Policy.class, id);
            if (p == null) {
                continue;
            }
            try {
                policies++;
                NativePolicyConstraintMapper.mapInto(p, result.getConstraints(),
                        config.getSourceSystem(), config.getExtractionRunId());
            } finally {
                context.decache(p);
            }
        }
        result.setPolicyCount(policies);
        result.setFinishedAt(Instant.now());
        return result;
    }
}
