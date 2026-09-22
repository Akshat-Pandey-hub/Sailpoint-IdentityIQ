package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeRoleMapper;
import com.keyforge.nativeiiq.model.NativeRoleExtractionResult;
import com.keyforge.nativeiiq.model.NativeRoleRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for Role ({@code sailpoint.object.Bundle}) — the Java-API equivalent of the
 * REST role service. Same flow as the validated native extractors (project ids, load one at a time,
 * map, decache). STRICTLY READ-ONLY: only {@code search}/{@code getObjectById}/{@code decache}.
 * Ordering is by {@code name} (present and unique on a Bundle) so bounded pagination is deterministic.
 */
public final class NativeRoleExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeRoleExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeRoleExtractionResult extract(int start, int limit) throws GeneralException {
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

    private NativeRoleExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeRoleExtractionResult result = new NativeRoleExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "Bundle", Instant.now());

        Iterator<Object[]> ids = context.search(Bundle.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Bundle bundle = context.getObjectById(Bundle.class, id);
            if (bundle == null) {
                continue;
            }
            try {
                NativeRoleRow row = NativeRoleMapper.map(bundle, config.getSourceSystem(), config.getExtractionRunId());
                result.getRoles().add(row);
            } finally {
                context.decache(bundle);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
