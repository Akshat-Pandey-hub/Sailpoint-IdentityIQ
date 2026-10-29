package com.keyforge.nativeiiq.source;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

import java.util.List;

/**
 * Shared read-only scan used by the three Access-History source extractors. Given the Access-History-bound
 * {@link SailPointContext}, it counts, pages and materializes full objects, mapping each to a row and
 * decaching it. Uses {@code getObjects(...)} (full objects) rather than a projection {@code search(...,"id")}
 * — the supported read path for these objects, matching IIQ's own capture stores. Ordered by the immutable
 * PK {@code id} for deterministic pagination. Never skips a non-null object; preserves an empty source as an
 * empty page.
 */
final class NativeAccessHistoryScan {

    /** Maps one live SailPoint Access-History object to a native row. */
    interface RowMapper<T extends SailPointObject, R> {
        R map(T object);
    }

    private NativeAccessHistoryScan() {
    }

    /**
     * Counts the total source rows (the live {@code countObjects}) and appends one mapped row per object in
     * the requested page to {@code out}. Returns the total source count so the caller can guard completeness.
     */
    static <T extends SailPointObject, R> int scan(SailPointContext ah, Class<T> cls, int start, int limit,
                                                   RowMapper<T, R> mapper, List<R> out) throws GeneralException {
        return scan(ah, cls, start, limit, null, mapper, out);
    }

    /**
     * Same, optionally bounded to records created after {@code createdAfter} (server-side {@code created >};
     * {@code null} = all records, unchanged behaviour). The returned source count uses the same bound.
     */
    static <T extends SailPointObject, R> int scan(SailPointContext ah, Class<T> cls, int start, int limit,
                                                   java.util.Date createdAfter, RowMapper<T, R> mapper,
                                                   List<R> out) throws GeneralException {
        return scan(ah, cls, start, limit, createdAfter, true, mapper, out);
    }

    /**
     * Same; the population is counted only when {@code includeCount} is true. Counting reads the whole (bounded)
     * population, so a caller that only needs the page must not ask for it; {@code -1} is returned then.
     */
    static <T extends SailPointObject, R> int scan(SailPointContext ah, Class<T> cls, int start, int limit,
                                                   java.util.Date createdAfter, boolean includeCount,
                                                   RowMapper<T, R> mapper, List<R> out) throws GeneralException {
        int sourceCount = includeCount
                ? ah.countObjects(cls, NativeIncrementalQuery.applyCreatedAfter(new QueryOptions(), createdAfter))
                : -1;

        QueryOptions qo = new QueryOptions();
        NativeIncrementalQuery.applyCreatedAfter(qo, createdAfter);
        qo.addOrdering("id", true);
        if (start > 0) {
            qo.setFirstRow(start);
        }
        if (limit > 0) {
            qo.setResultLimit(limit);
        }

        List<T> page = ah.getObjects(cls, qo);
        try {
            if (page != null) {
                for (T o : page) {
                    if (o != null) {
                        out.add(mapper.map(o));
                    }
                }
            }
        } finally {
            // getObjects materializes the whole page; release all of it even if mapping one row fails.
            if (page != null) {
                for (T o : page) {
                    if (o != null) ah.decache(o);
                }
            }
        }
        return sourceCount;
    }
}
