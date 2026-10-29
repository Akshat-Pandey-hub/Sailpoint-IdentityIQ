package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeIdentityRoleMapper;
import com.keyforge.nativeiiq.model.NativeIdentityRoleExtractionResult;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Date;
import java.util.Iterator;

/**
 * Native source adapter for identity&nbsp;&harr;&nbsp;role edges. The paginated source unit is the
 * {@code Identity} (ordered by id); each is expanded into ASSIGNED/DETECTED role edges. STRICTLY READ-ONLY.
 */
public final class NativeIdentityRoleExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeIdentityRoleExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeIdentityRoleExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    public NativeIdentityRoleExtractionResult extract(int start, int limit, Date modifiedAfter) throws GeneralException {
        return extract(start, limit, modifiedAfter, true);
    }

    /**
     * One page; the population is counted only when {@code includeCount} is true. Counting reads the whole
     * (bounded) population, so a caller that only needs the page must not ask for it; the result then reports
     * {@code -1} (not counted).
     */
    public NativeIdentityRoleExtractionResult extract(int start, int limit, Date modifiedAfter,
            boolean includeCount) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        NativeIncrementalQuery.applyModifiedAfter(qo, modifiedAfter);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeIdentityRoleExtractionResult result = new NativeIdentityRoleExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "IdentityRole", Instant.now());
        QueryOptions countOptions = new QueryOptions();
        NativeIncrementalQuery.applyModifiedAfter(countOptions, modifiedAfter);
        if (includeCount) {
            result.setSourceCount(context.countObjects(Identity.class, countOptions));
        }

        int identities = 0;
        Iterator<Object[]> ids = context.search(Identity.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                Identity identity = context.getObjectById(Identity.class, id);
                if (identity == null) {
                    continue;
                }
                try {
                    identities++;
                    NativeIdentityRoleMapper.mapInto(
                            identity, result.getRows(), config.getSourceSystem(), config.getExtractionRunId());
                } finally {
                    context.decache(identity);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }
        result.setIdentityCount(identities);
        result.setFinishedAt(Instant.now());
        return result;
    }
}
