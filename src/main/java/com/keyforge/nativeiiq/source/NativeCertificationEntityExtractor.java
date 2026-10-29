package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeCertificationEntityMapper;
import com.keyforge.nativeiiq.model.NativeCertificationEntityExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationEntityRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationEntity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.CertificationEntity}. Read-only: {@code countObjects}/
 * {@code search}/{@code getObjectById}/{@code decache}. Ordered by id for deterministic pagination.
 */
public final class NativeCertificationEntityExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeCertificationEntityExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeCertificationEntityExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * One page, optionally bounded to objects changed after {@code modifiedAfter} (server-side: modified after it,
     * or never modified and created after it; {@code null} = the full population, unchanged behaviour). The
     * source count uses the same bound, so the caller's complete-scan guard compares like with like.
     */
    public NativeCertificationEntityExtractionResult extract(int start, int limit, java.util.Date modifiedAfter) throws GeneralException {
        return extract(start, limit, modifiedAfter, true);
    }

    /**
     * One page; the population is counted only when {@code includeCount} is true. Counting reads the whole
     * (bounded) population, so a caller that only needs the page must not ask for it; the result then reports
     * {@code -1} (not counted).
     */
    public NativeCertificationEntityExtractionResult extract(int start, int limit, java.util.Date modifiedAfter,
            boolean includeCount) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyModifiedAfter(qo, modifiedAfter);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeCertificationEntityExtractionResult result = new NativeCertificationEntityExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "CertificationEntity", Instant.now());
        if (includeCount) {
            result.setSourceCount(context.countObjects(CertificationEntity.class,
                    NativeIncrementalQuery.applyModifiedAfter(new QueryOptions(), modifiedAfter)));
        }

        Iterator<Object[]> ids = context.search(CertificationEntity.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                CertificationEntity e = context.getObjectById(CertificationEntity.class, id);
                if (e == null) {
                    continue;
                }
                try {
                    NativeCertificationEntityRow row = NativeCertificationEntityMapper.map(
                            e, config.getSourceSystem(), config.getExtractionRunId());
                    result.getRows().add(row);
                } finally {
                    context.decache(e);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }
        result.setFinishedAt(Instant.now());
        return result;
    }
}
