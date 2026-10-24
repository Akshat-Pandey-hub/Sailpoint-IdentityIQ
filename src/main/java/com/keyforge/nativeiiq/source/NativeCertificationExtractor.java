package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeCertificationMapper;
import com.keyforge.nativeiiq.model.NativeCertificationExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Certification;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Date;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.Certification}. Read-only: {@code countObjects}/
 * {@code search}/{@code getObjectById}/{@code decache}. Ordered by id for deterministic pagination.
 */
public final class NativeCertificationExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeCertificationExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeCertificationExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    public NativeCertificationExtractionResult extract(int start, int limit, Date modifiedAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        NativeIncrementalQuery.applyModifiedAfter(qo, modifiedAfter);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeCertificationExtractionResult result = new NativeCertificationExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "Certification", Instant.now());
        result.setSourceCount(context.countObjects(Certification.class, NativeIncrementalQuery.applyModifiedAfter(new QueryOptions(), modifiedAfter)));

        Iterator<Object[]> ids = context.search(Certification.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                Certification c = context.getObjectById(Certification.class, id);
                if (c == null) {
                    continue;
                }
                try {
                    NativeCertificationRow row = NativeCertificationMapper.map(
                            c, config.getSourceSystem(), config.getExtractionRunId());
                    result.getRows().add(row);
                } finally {
                    context.decache(c);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }
        result.setFinishedAt(Instant.now());
        return result;
    }
}
