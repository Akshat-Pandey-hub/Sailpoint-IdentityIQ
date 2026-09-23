package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeIdentityEntitlementMapper;
import com.keyforge.nativeiiq.model.NativeIdentityEntitlementExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityEntitlementRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.IdentityEntitlement} (identity&nbsp;&harr;&nbsp;
 * entitlement with provenance). Same read-only flow as the validated native extractors (project ids, load
 * one at a time, map, decache). STRICTLY READ-ONLY: only {@code countObjects}/{@code search}/
 * {@code getObjectById}/{@code decache}. Ordering by {@code id} for deterministic pagination; the
 * authoritative {@code countObjects} total is captured for source/reconciliation and the import-side
 * incomplete-scan guard.
 */
public final class NativeIdentityEntitlementExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeIdentityEntitlementExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeIdentityEntitlementExtractionResult extract(int start, int limit) throws GeneralException {
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

    private NativeIdentityEntitlementExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeIdentityEntitlementExtractionResult result = new NativeIdentityEntitlementExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "IdentityEntitlement", Instant.now());

        result.setSourceCount(context.countObjects(IdentityEntitlement.class, new QueryOptions()));

        Iterator<Object[]> ids = context.search(IdentityEntitlement.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            IdentityEntitlement e = context.getObjectById(IdentityEntitlement.class, id);
            if (e == null) {
                continue;
            }
            try {
                NativeIdentityEntitlementRow row = NativeIdentityEntitlementMapper.map(
                        e, config.getSourceSystem(), config.getExtractionRunId());
                result.getRows().add(row);
            } finally {
                context.decache(e);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
