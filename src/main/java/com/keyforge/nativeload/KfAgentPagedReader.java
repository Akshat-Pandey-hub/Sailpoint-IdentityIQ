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
            String json = fetch.fetchPage(offset, chunk);
            List<R> page = parse.apply(json);
            out.addAll(page);
            if (isLastPage(json, page.size(), chunk)) {
                break;
            }
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
            String json = fetch.fetchPage(offset, scanPageSize);
            List<R> page = parse.apply(json);
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
            if (isLastPage(json, page.size(), scanPageSize)) {
                return out;
            }
            offset += page.size();
        }
    }

    /**
     * True when this page ends the population: it is empty, or shorter than what was asked for (taking the plugin's
     * echoed effective {@code limit} into account, in case it clamped the request).
     */
    private static boolean isLastPage(String json, int returned, int requested) {
        if (returned == 0) {
            return true;
        }
        int effective = effectiveLimit(json);
        int expected = effective > 0 ? Math.min(effective, requested) : requested;
        return returned < expected;
    }

    /** The {@code limit} the plugin reports it applied, or -1 when absent. */
    static int effectiveLimit(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode n = root == null ? null : root.get("limit");
            return n != null && n.canConvertToInt() ? n.asInt() : -1;
        } catch (Exception malformed) {
            return -1; // the parser reports a malformed body itself
        }
    }
}
