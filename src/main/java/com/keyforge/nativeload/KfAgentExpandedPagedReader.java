package com.keyforge.nativeload;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Pages source objects and windows their expanded REST rows without retaining the full population. */
final class KfAgentExpandedPagedReader<R> {
    interface Fetch { String fetch(int start, int limit); }

    private final Fetch fetch;
    interface PageParser<R> { Page<R> parse(String json, int requestedStart, int requestedLimit); }
    static final class Page<R> {
        final List<R> rows;
        final int nextStart;
        final boolean done;
        final int sourceTotal;
        Page(List<R> rows, int nextStart, boolean done) { this(rows, nextStart, done, -1); }
        Page(List<R> rows, int nextStart, boolean done, int sourceTotal) {
            this.rows = rows;
            this.nextStart = nextStart;
            this.done = done;
            this.sourceTotal = sourceTotal;
        }
    }

    private final PageParser<R> parse;
    private final int pageSize;

    KfAgentExpandedPagedReader(Fetch fetch, PageParser<R> parse, int pageSize) {
        this.fetch = fetch;
        this.parse = parse;
        this.pageSize = pageSize;
    }

    /**
     * True for a whole-population request: no filters (null or empty — the HTTP handler always passes a map), no
     * {@code start} (or 0) and no {@code limit} (or negative, which means "no limit"). Only that request takes the full
     * scan with its completeness evidence; every other request is served by {@link #read}.
     */
    static boolean isFullSnapshot(java.util.Map<String, String> filters, Integer start, Integer limit) {
        return (filters == null || filters.isEmpty()) && (start == null || start <= 0) && (limit == null || limit < 0);
    }

    /**
     * The matching expanded rows at positions {@code [start, start + limit)} (all from {@code start} when
     * {@code limit} is null or negative). Source pages are read in order from the first source object, because
     * {@code start}/{@code limit} count expanded rows, not source objects; reading stops once the window is filled.
     */
    List<R> read(Predicate<R> matches, Integer requestedStart, Integer requestedLimit) {
        int skip = requestedStart == null ? 0 : Math.max(0, requestedStart);
        long wanted = requestedLimit == null || requestedLimit < 0
                ? Long.MAX_VALUE : requestedLimit;
        // Always a full page of source objects: a source object can expand to zero rows, so shrinking the page to
        // start+limit would cost one plugin call per object when matches are sparse.
        int readPageSize = pageSize;
        List<R> result = new ArrayList<>();
        if (wanted == 0) return result;

        SourceCountGuard sourceCountGuard = new SourceCountGuard("Expanded entity", ignored -> -1);
        int sourceStart = 0;
        int scannedSourceRows = 0;
        while (true) {
            String json = fetch.fetch(sourceStart, readPageSize);
            Page<R> page = parse.parse(json, sourceStart, readPageSize);
            sourceCountGuard.checkCount(page.sourceTotal);
            for (R row : page.rows) {
                if (!matches.test(row)) continue;
                if (skip > 0) {
                    skip--;
                    continue;
                }
                result.add(row);
                if (result.size() >= wanted) return result;
            }

            scannedSourceRows += page.nextStart - sourceStart;
            if (page.done || page.nextStart - sourceStart < readPageSize) {
                if (page.sourceTotal >= 0 && scannedSourceRows != page.sourceTotal)
                    throw new NativeImportException("Incomplete expanded entity scan: source count="
                            + page.sourceTotal + ", scanned=" + scannedSourceRows);
                return result;
            }
            if (page.nextStart <= sourceStart)
                throw new NativeImportException("Expanded entity pagination did not advance (start=" + sourceStart + ")");
            sourceStart = page.nextStart;
        }
    }
}
