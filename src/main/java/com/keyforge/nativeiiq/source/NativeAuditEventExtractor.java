package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeAuditEventMapper;
import com.keyforge.nativeiiq.model.NativeAuditEventExtractionResult;
import com.keyforge.nativeiiq.model.NativeAuditEventRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.AuditEvent;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for {@code sailpoint.object.AuditEvent} — immutable historical audit records.
 * Read-only ({@code search}/{@code getObjectById}/{@code decache}); emits the total {@code countObjects}
 * so the client can guard a complete scan. Ordered by {@code created} then {@code id} for deterministic
 * pagination over a large, append-heavy log.
 */
public final class NativeAuditEventExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeAuditEventExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeAuditEventExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("created", true);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo);
    }

    private NativeAuditEventExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeAuditEventExtractionResult result = new NativeAuditEventExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "AuditEvent", Instant.now());

        result.setSourceCount(context.countObjects(AuditEvent.class, new QueryOptions()));

        Iterator<Object[]> ids = context.search(AuditEvent.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            AuditEvent a = context.getObjectById(AuditEvent.class, id);
            if (a == null) {
                continue;
            }
            try {
                NativeAuditEventRow row = NativeAuditEventMapper.map(
                        a, config.getSourceSystem(), config.getExtractionRunId());
                result.getAuditEvents().add(row);
            } finally {
                context.decache(a);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
