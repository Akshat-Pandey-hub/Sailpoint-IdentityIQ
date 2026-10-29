package com.keyforge.nativeload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bounded reads of one KF Agent window from a plugin endpoint that pages its source objects 1:1 in a fixed order
 * ({@code firstRow=start}, {@code resultLimit<=max}, ordered by id). Used instead of collecting the whole population
 * when a request does not need it:
 * <ul>
 *   <li>{@link #window}: an UNFILTERED {@code start}/{@code limit} request reads only that window, chunked by the
 *       plugin's maximum page size. It is the same slice of the same ordered population the full scan returns.</li>
 *   <li>{@link #filtered}: a FILTERED request must still look at every candidate (a match can be on any page), so it
 *       walks the pages in the same order as the full scan, but keeps only matching records and stops as soon as it
 *       has {@code start + limit} matches. Memory holds matches only, never the whole population.</li>
 * </ul>
 * A request with no filters and no {@code limit} is a whole-population snapshot and is not served here.
 *
 * @param <R> the parsed record type
 */
final class KfAgentPagedReader<R> {

    /** Reads one plugin page as the raw JSON envelope. */
    interface PageFetch {
        String fetchPage(int start, int limit);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PageFetch fetch;
    private final Function<String, List<R>> parse;
    private final int pluginMaxPage;
    private final int scanPageSize;

    /**
     * @param fetch         the plugin page source
     * @param parse         envelope JSON to records; must throw on an error envelope or malformed body
     * @param pluginMaxPage the plugin's per-call maximum ({@code MAX_LIMIT}); window chunks never exceed it
     * @param scanPageSize  page size for filtered scans (the same size the full scan uses)
     */
    KfAgentPagedReader(PageFetch fetch, Function<String, List<R>> parse, int pluginMaxPage, int scanPageSize) {
        this.fetch = fetch;
        this.parse = parse;
        this.pluginMaxPage = pluginMaxPage;
        this.scanPageSize = Math.min(scanPageSize, pluginMaxPage);
    }

    /** The records at positions {@code [from, from + limit)} of the ordered population (fewer at the end). */
    List<R> window(int from, int limit) {
        List<R> out = new ArrayList<>(Math.min(Math.max(limit, 0), pluginMaxPage));
        int offset = Math.max(0, from);
        int remaining = limit;
        while (remaining > 0) {
            int chunk = Math.min(remaining, pluginMaxPage);
            List<R> page = parse.apply(fetch.fetchPage(offset, chunk));
            if (isEnd(page)) {
                break;
            }
            out.addAll(page);
            offset += page.size();
            remaining -= page.size();
        }
        return out;
    }

    /**
     * The matching records at match positions {@code [from, from + limit)} (all matches from {@code from} when
     * {@code limit} is null). Pages are read in population order and reading stops once the window is filled.
     */
    List<R> filtered(Predicate<R> matches, int from, Integer limit) {
        int skip = Math.max(0, from);
        long wanted = limit == null ? Long.MAX_VALUE : Math.max(0, limit);
        List<R> out = new ArrayList<>();
        if (wanted == 0) {
            return out;
        }
        int matched = 0;
        int offset = 0;
        while (true) {
            List<R> page = parse.apply(fetch.fetchPage(offset, scanPageSize));
            if (isEnd(page)) {
                return out;
            }
            for (R r : page) {
                if (!matches.test(r)) {
                    continue;
                }
                if (matched++ < skip) {
                    continue;
                }
                out.add(r);
                if (out.size() >= wanted) {
                    return out;
                }
            }
            offset += page.size();
        }
    }

    /**
     * The end-of-data rule for every paged plugin read: only an EMPTY page ends it. A page shorter than asked is not
     * the end, because an object deleted while IIQ loaded that page leaves it short and shifts the later objects up
     * one place; the caller advances by what it received and reads on. At the true end this costs one extra call,
     * which comes back empty.
     */
    static boolean isEnd(List<?> page) {
        return page == null || page.isEmpty();
    }
}
