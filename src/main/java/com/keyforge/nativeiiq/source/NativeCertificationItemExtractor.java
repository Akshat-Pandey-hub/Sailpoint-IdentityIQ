package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeCertificationItemMapper;
import com.keyforge.nativeiiq.model.NativeCertificationItemExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationItemRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.CertificationItem;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.CertificationItem}. Read-only: {@code countObjects}/
 * {@code search}/{@code getObjectById}/{@code decache}. Ordered by id for deterministic pagination.
 */
public final class NativeCertificationItemExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeCertificationItemExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeCertificationItemExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeCertificationItemExtractionResult result = new NativeCertificationItemExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "CertificationItem", Instant.now());
        result.setSourceCount(context.countObjects(CertificationItem.class, new QueryOptions()));

        Iterator<Object[]> ids = context.search(CertificationItem.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            CertificationItem c = context.getObjectById(CertificationItem.class, id);
            if (c == null) {
                continue;
            }
            try {
                NativeCertificationItemRow row = NativeCertificationItemMapper.map(
                        c, config.getSourceSystem(), config.getExtractionRunId());
                result.getRows().add(row);
            } finally {
                context.decache(c);
            }
        }
        result.setFinishedAt(Instant.now());
        return result;
    }
}
