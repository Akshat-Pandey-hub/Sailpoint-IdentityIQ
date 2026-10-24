package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.config.NativeExtractionConfig;
import com.keyforge.nativeiiq.mapper.NativeAccountEntitlementMapper;
import com.keyforge.nativeiiq.model.NativeAccountEntitlementExtractionResult;

import sailpoint.api.SailPointContext;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.time.Instant;
import java.util.Iterator;

/**
 * Native source adapter for account&nbsp;&harr;&nbsp;entitlement edges, read from {@code Link}s. The
 * paginated source unit is the {@code Link} (ordered by id); each Link is expanded into its entitlement
 * edges via {@link NativeAccountEntitlementMapper}. STRICTLY READ-ONLY: only {@code countObjects}/
 * {@code search}/{@code getObjectById}/{@code decache}. The authoritative total Link count is captured for
 * the importer's incomplete-scan guard; {@code linkCount} reports how many Links this page covered.
 */
public final class NativeAccountEntitlementExtractor {

    private final SailPointContext context;
    private final NativeExtractionConfig config;

    public NativeAccountEntitlementExtractor(SailPointContext context, NativeExtractionConfig config) {
        this.context = context;
        this.config = config;
    }

    public NativeAccountEntitlementExtractionResult extract(int start, int limit) throws GeneralException {
        return extract(start, limit, null);
    }

    /**
     * One page of accounts (Links), each expanded into its entitlement edges, optionally bounded to those changed after {@code modifiedAfter} (server-side: modified
     * after it, or never modified and created after it; {@code null} = the full population, unchanged behaviour).
     * The source count uses the same bound, so the caller's complete-scan guard compares like with like.
     */
    public NativeAccountEntitlementExtractionResult extract(int start, int limit, java.util.Date modifiedAfter) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyModifiedAfter(qo, modifiedAfter);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }
        return extract(qo, modifiedAfter);
    }

    private NativeAccountEntitlementExtractionResult extract(QueryOptions qo, java.util.Date modifiedAfter) throws GeneralException {
        NativeAccountEntitlementExtractionResult result = new NativeAccountEntitlementExtractionResult(
                config.getSourceSystem(), config.getExtractionRunId(), "AccountEntitlement", Instant.now());

        result.setSourceCount(context.countObjects(Link.class,
                NativeIncrementalQuery.applyModifiedAfter(new QueryOptions(), modifiedAfter)));

        int links = 0;
        Iterator<Object[]> ids = context.search(Link.class, qo, "id");
        try {
            while (ids != null && ids.hasNext()) {
                String id = (String) ids.next()[0];
                Link link = context.getObjectById(Link.class, id);
                if (link == null) {
                    continue;
                }
                try {
                    links++;
                    NativeAccountEntitlementMapper.mapInto(
                            link, result.getRows(), config.getSourceSystem(), config.getExtractionRunId());
                } finally {
                    context.decache(link);
                }
            }
        } finally {
            sailpoint.tools.Util.flushIterator(ids);
        }

        result.setLinkCount(links);
        result.setFinishedAt(Instant.now());
        return result;
    }
}
