package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeGroupDefinitionMapper;
import com.keyforge.nativeiiq.model.NativeGroupDefinitionExtractionResult;
import com.keyforge.nativeiiq.model.NativeGroupDefinitionRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.GroupDefinition;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.GroupDefinition} (Populations and Groups). Same
 * flow as the validated native extractors (project ids, load one at a time, map, decache). STRICTLY
 * READ-ONLY: only {@code search}/{@code getObjectById}/{@code decache}. Ordering by {@code name} for
 * deterministic pagination.
 */
public final class NativeGroupDefinitionExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeGroupDefinitionExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeGroupDefinitionExtractionResult extract(int start, int limit) throws GeneralException {
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

    private NativeGroupDefinitionExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeGroupDefinitionExtractionResult result = new NativeGroupDefinitionExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "GroupDefinition", Instant.now());

        Iterator<Object[]> ids = context.search(GroupDefinition.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            GroupDefinition gd = context.getObjectById(GroupDefinition.class, id);
            if (gd == null) {
                continue;
            }
            try {
                NativeGroupDefinitionRow row = NativeGroupDefinitionMapper.map(
                        gd, config.getSourceSystem(), config.getExtractionRunId());
                result.getGroupDefinitions().add(row);
            } finally {
                context.decache(gd);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
