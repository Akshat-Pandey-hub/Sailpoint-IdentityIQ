package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeApplicationMapper;
import com.keyforge.nativeiiq.model.NativeApplicationExtractionResult;
import com.keyforge.nativeiiq.model.NativeApplicationRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for Application — the Java-API equivalent of the REST application service.
 * Same flow as the validated Identity/ManagedAttribute extractors (project ids, load one at a time,
 * map, decache). STRICTLY READ-ONLY: only {@code search}/{@code getObjectById}/{@code decache}.
 *
 * <p>Ordering is by {@code name} (always present and unique on an Application) so bounded pagination is
 * deterministic. Streams ids and decaches each object so memory stays bounded.
 */
public final class NativeApplicationExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeApplicationExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    /** Extracts a stable-ordered window (rows {@code start}..{@code start+limit}); {@code limit<=0}=all. */
    public NativeApplicationExtractionResult extract(int start, int limit) throws GeneralException {
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

    private NativeApplicationExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeApplicationExtractionResult result = new NativeApplicationExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "Application", Instant.now());

        Iterator<Object[]> ids = context.search(Application.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Application app = context.getObjectById(Application.class, id);
            if (app == null) {
                continue;
            }
            try {
                NativeApplicationRow row = NativeApplicationMapper.map(
                        app, config.getSourceSystem(), config.getExtractionRunId());
                result.getApplications().add(row);
            } finally {
                context.decache(app);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
