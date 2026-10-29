package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeProvisioningTxnMapper;
import com.keyforge.nativeiiq.model.NativeProvisioningTxnExtractionResult;
import com.keyforge.nativeiiq.model.NativeProvisioningTxnRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.ProvisioningTransaction}. Read-only: {@code countObjects}/
 * {@code search}/{@code getObjectById}/{@code decache}. Ordered by id for deterministic pagination; the
 * derived provisioning items ride along inside each transaction row.
 */
public final class NativeProvisioningTxnExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeProvisioningTxnExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeProvisioningTxnExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * One page, optionally bounded to objects changed after {@code modifiedAfter} (server-side: modified after it,
     * or never modified and created after it; {@code null} = the full population, unchanged behaviour). The
     * source count uses the same bound, so the caller's complete-scan guard compares like with like.
     */
    public NativeProvisioningTxnExtractionResult extract(int start, int limit, java.util.Date modifiedAfter) throws GeneralException {
        return extract(start, limit, modifiedAfter, true);
    }

    /**
     * One page; the population is counted only when {@code includeCount} is true. Counting reads the whole
     * (bounded) population, so a caller that only needs the page must not ask for it; the result then reports
     * {@code -1} (not counted).
     */
    public NativeProvisioningTxnExtractionResult extract(int start, int limit, java.util.Date modifiedAfter,
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
        NativeProvisioningTxnExtractionResult result = new NativeProvisioningTxnExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "ProvisioningTransaction", Instant.now());
        if (includeCount) {
            result.setSourceCount(context.countObjects(ProvisioningTransaction.class,
                    NativeIncrementalQuery.applyModifiedAfter(new QueryOptions(), modifiedAfter)));
        }

        Iterator<Object[]> ids = context.search(ProvisioningTransaction.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                ProvisioningTransaction pt = context.getObjectById(ProvisioningTransaction.class, id);
                if (pt == null) {
                    continue;
                }
                try {
                    NativeProvisioningTxnRow row = NativeProvisioningTxnMapper.map(
                            pt, config.getSourceSystem(), config.getExtractionRunId());
                    result.getRows().add(row);
                } finally {
                    context.decache(pt);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }
        result.setFinishedAt(Instant.now());
        return result;
    }
}
