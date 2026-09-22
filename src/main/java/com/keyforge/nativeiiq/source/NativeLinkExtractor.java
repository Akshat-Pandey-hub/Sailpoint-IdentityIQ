package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeLinkMapper;
import com.keyforge.nativeiiq.model.NativeLinkExtractionResult;
import com.keyforge.nativeiiq.model.NativeLinkRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for Link (account) — the Java-API equivalent of the REST account service.
 * Same flow as the validated Identity/ManagedAttribute/Application extractors (project ids, load one
 * at a time, map, decache). STRICTLY READ-ONLY: only {@code search}/{@code getObjectById}/
 * {@code decache}. Ordering is by {@code id} (always present and unique) so bounded pagination is
 * deterministic across pages.
 */
public final class NativeLinkExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeLinkExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeLinkExtractionResult extract(int start, int limit) throws GeneralException {
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

    private NativeLinkExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeLinkExtractionResult result = new NativeLinkExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "Link", Instant.now());

        Iterator<Object[]> ids = context.search(Link.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Link link = context.getObjectById(Link.class, id);
            if (link == null) {
                continue;
            }
            try {
                NativeLinkRow row = NativeLinkMapper.map(link, config.getSourceSystem(), config.getExtractionRunId());
                result.getLinks().add(row);
            } finally {
                context.decache(link);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
