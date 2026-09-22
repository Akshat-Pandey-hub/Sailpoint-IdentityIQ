package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeIdentityMapper;
import com.keyforge.nativeiiq.model.NativeExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityRow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for Identity — the Java-API equivalent of the REST {@code IdentityService}.
 * Same flow (query source → map), but the source is the IIQ object model via {@link SailPointContext}
 * instead of SCIM/HTTP. STRICTLY READ-ONLY: it uses only {@code search}/{@code getObjectById}/
 * {@code decache}; it never saves, deletes, provisions, or triggers workflows.
 *
 * <p>Does not persist — it returns a {@link NativeExtractionResult} (the output abstraction). The
 * IIQ→DB transport is a later phase. Streams ids and loads+decaches each Identity so memory stays
 * bounded; honours an optional test limit from config.
 */
public final class NativeIdentityExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeIdentityExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeExtractionResult extract() throws GeneralException {
        QueryOptions qo = new QueryOptions();
        if (config.hasIdentityLimit()) {
            qo.setResultLimit(config.getIdentityLimit()); // bounded/test extraction
        }
        return extract(qo);
    }

    /**
     * Paginated read for the plugin REST endpoint: a stable-ordered window of Identity rows
     * (rows {@code start}..{@code start+limit}). Ordering by {@code name} makes paging deterministic
     * across calls. {@code limit <= 0} means no limit. STRICTLY READ-ONLY, same as {@link #extract()}.
     */
    public NativeExtractionResult extract(int start, int limit) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addOrdering("name", true); // ascending, stable window across pages
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo);
    }

    private NativeExtractionResult extract(QueryOptions qo) throws GeneralException {
        NativeExtractionResult result = new NativeExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "Identity", Instant.now());

        // Project only ids first (cheap), then load full objects one at a time and release from cache.
        Iterator<Object[]> ids = context.search(Identity.class, qo, "id");
        while (ids != null && ids.hasNext()) {
            String id = (String) ids.next()[0];
            Identity idn = context.getObjectById(Identity.class, id);
            if (idn == null) {
                continue;
            }
            try {
                NativeIdentityRow row = NativeIdentityMapper.map(
                        idn, config.getSourceSystem(), config.getExtractionRunId());
                result.getIdentities().add(row);
            } finally {
                context.decache(idn);
            }
        }

        result.setFinishedAt(Instant.now());
        return result;
    }
}
