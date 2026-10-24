package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeCertificationArchiveMapper;
import com.keyforge.nativeiiq.model.NativeCertificationArchiveExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationArchiveRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationArchive;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.CertificationArchive} — the immutable CEC/history record
 * of completed certifications. Same read-only flow as the validated native extractors (project ids, load one
 * at a time, map, decache). STRICTLY READ-ONLY: only {@code search}/{@code getObjectById}/{@code decache}.
 * Ordering by {@code id} for deterministic pagination (always present, unlike name/created).
 */
public final class NativeCertificationArchiveExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeCertificationArchiveExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeCertificationArchiveExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * One page, optionally bounded to objects created after {@code createdAfter} (server-side
     * {@code created >} filter; {@code null} = the full population, unchanged behaviour). The source count
     * uses the same bound, so the caller's complete-scan guard compares like with like.
     */
    public NativeCertificationArchiveExtractionResult extract(int start, int limit, java.util.Date createdAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyCreatedAfter(qo, createdAfter);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo, createdAfter);
    }

    private NativeCertificationArchiveExtractionResult extract(QueryOptions qo, java.util.Date createdAfter) throws GeneralException {
        NativeCertificationArchiveExtractionResult result = new NativeCertificationArchiveExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "CertificationArchive", Instant.now());

        // Count independently of the requested page for full-scan reconciliation by the client.
        result.setSourceCount(context.countObjects(CertificationArchive.class,
                NativeIncrementalQuery.applyCreatedAfter(new QueryOptions(), createdAfter)));

        Iterator<Object[]> ids = context.search(CertificationArchive.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                CertificationArchive a = context.getObjectById(CertificationArchive.class, id);
                if (a == null) {
                    continue;
                }
                try {
                    NativeCertificationArchiveRow row = NativeCertificationArchiveMapper.map(
                            a, config.getSourceSystem(), config.getExtractionRunId());
                    result.getCertificationArchives().add(row);
                } finally {
                    context.decache(a);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
