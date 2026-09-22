package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeWorkgroupMapper;
import com.keyforge.nativeiiq.model.NativeWorkgroupExtractionResult;
import com.keyforge.nativeiiq.model.NativeWorkgroupRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for Workgroup. IIQ has no dedicated Workgroup class, so this queries
 * {@code sailpoint.object.Identity} filtered to {@code workgroup == true} — the authoritative native
 * representation of a governance workgroup. Ordinary identities are excluded by the filter (so they
 * are never duplicated into kf_workgroup). STRICTLY READ-ONLY: only {@code search}/{@code getObjectById}/
 * {@code decache}. Ordering by {@code name} for deterministic pagination.
 */
public final class NativeWorkgroupExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeWorkgroupExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeWorkgroupExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("workgroup", true)); // the authoritative workgroup flag
        qo.addOrdering("name", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo);
    }

    private NativeWorkgroupExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeWorkgroupExtractionResult result = new NativeWorkgroupExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "Workgroup", Instant.now());

        Iterator<Object[]> ids = context.search(Identity.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Identity wg = context.getObjectById(Identity.class, id);
            if (wg == null) {
                continue;
            }
            try {
                NativeWorkgroupRow row = NativeWorkgroupMapper.map(
                        wg, config.getSourceSystem(), config.getExtractionRunId());
                result.getWorkgroups().add(row);
            } finally {
                context.decache(wg);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
