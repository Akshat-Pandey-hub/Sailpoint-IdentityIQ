package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeManagedAttributeMapper;
import com.keyforge.nativeiiq.model.NativeManagedAttributeExtractionResult;
import com.keyforge.nativeiiq.model.NativeManagedAttributeRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for ManagedAttribute — the Java-API equivalent of the REST entitlement
 * service. Same flow as {@code NativeIdentityExtractor} (project ids, load one at a time, map,
 * decache), but the source object is {@code sailpoint.object.ManagedAttribute}. STRICTLY READ-ONLY:
 * only {@code search}/{@code getObjectById}/{@code decache}; never saves, deletes, or provisions.
 *
 * <p>Ordering is by {@code id} (always present and unique) so bounded pagination is deterministic —
 * ManagedAttribute {@code name} can be null. Streams ids and decaches each object so memory stays
 * bounded; honours a bounded page.
 */
public final class NativeManagedAttributeExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeManagedAttributeExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    /**
     * Extracts a stable-ordered window of managed attributes (rows {@code start}..{@code start+limit}).
     * {@code limit <= 0} means no limit (full extraction).
     */
    public NativeManagedAttributeExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("id", true); // deterministic; name may be null on a ManagedAttribute
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo);
    }

    private NativeManagedAttributeExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeManagedAttributeExtractionResult result = new NativeManagedAttributeExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "ManagedAttribute", Instant.now());

        Iterator<Object[]> ids = context.search(ManagedAttribute.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            ManagedAttribute ma = context.getObjectById(ManagedAttribute.class, id);
            if (ma == null) {
                continue;
            }
            try {
                NativeManagedAttributeRow row = NativeManagedAttributeMapper.map(
                        ma, config.getSourceSystem(), config.getExtractionRunId());
                result.getManagedAttributes().add(row);
            } finally {
                context.decache(ma);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
