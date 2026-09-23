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
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        NativeProvisioningTxnExtractionResult result = new NativeProvisioningTxnExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "ProvisioningTransaction", Instant.now());
        result.setSourceCount(context.countObjects(ProvisioningTransaction.class, new QueryOptions()));

        Iterator<Object[]> ids = context.search(ProvisioningTransaction.class, qo, "id");
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
        result.setFinishedAt(Instant.now());
        return result;
    }
}
