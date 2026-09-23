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

    private NativeCertificationArchiveExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeCertificationArchiveExtractionResult result = new NativeCertificationArchiveExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "CertificationArchive", Instant.now());

        // Count independently of the requested page for full-scan reconciliation by the client.
        result.setSourceCount(context.countObjects(CertificationArchive.class, new QueryOptions()));

        Iterator<Object[]> ids = context.search(CertificationArchive.class, qo, "id");
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

        result.setFinishedAt(Instant.now());
        return result;
    }
}
