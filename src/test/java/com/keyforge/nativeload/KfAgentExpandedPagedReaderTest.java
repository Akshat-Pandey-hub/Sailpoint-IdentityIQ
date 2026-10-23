package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KfAgentExpandedPagedReaderTest {
    private record Row(int parent, int value) { }

    /** Plugin-like source of {@code parents} source objects; parent i expands to {@code rowsOf.apply(i)}. */
    private static final class Source {
        final int parents;
        final IntFunction<List<Row>> rowsOf;
        final List<String> calls = new ArrayList<>();
        int reportedTotal = -1;

        Source(int parents, IntFunction<List<Row>> rowsOf) {
            this.parents = parents;
            this.rowsOf = rowsOf;
        }

        KfAgentExpandedPagedReader<Row> reader() {
            return new KfAgentExpandedPagedReader<>(
                    (start, limit) -> {
                        calls.add(start + ":" + limit);
                        return start + "," + limit;
                    },
                    (json, start, requested) -> {
                        String[] args = json.split(",");
                        int offset = Integer.parseInt(args[0]);
                        int count = Math.max(0, Math.min(Integer.parseInt(args[1]), parents - offset));
                        List<Row> rows = new ArrayList<>();
                        for (int i = offset; i < offset + count; i++) {
                            rows.addAll(rowsOf.apply(i));
                        }
                        return new KfAgentExpandedPagedReader.Page<>(rows, offset + count, count < requested,
                                reportedTotal);
                    }, 500);
        }
    }

    private static List<Row> two(int i) {
        return List.of(new Row(i, i * 2), new Row(i, i * 2 + 1));
    }

    @Test
    void appliesOffsetThenLimitToExpandedRowsFromOneFullSourcePage() {
        Source source = new Source(4, KfAgentExpandedPagedReaderTest::two);

        List<Row> page = source.reader().read(row -> true, 1, 2);

        assertEquals(List.of(new Row(0, 1), new Row(1, 2)), page, "start/limit count expanded rows");
        assertEquals(List.of("0:500"), source.calls, "one full source page, starting at the first source object");
    }

    @Test
    void filteredMatchOnALaterSourcePageReadsOnlyUpToThatPage() {
        Source source = new Source(10_000, i -> i == 1_234 ? List.of(new Row(i, i)) : List.of());

        List<Row> page = source.reader().read(row -> row.value() == 1_234, null, 1);

        assertEquals(List.of(new Row(1_234, 1_234)), page);
        assertEquals(List.of("0:500", "500:500", "1000:500"), source.calls);
    }

    @Test
    void sparseRowsWithLimitOneDoNotCostOneCallPerSourceObject() {
        Source source = new Source(5_000, i -> i == 4_990 ? List.of(new Row(i, i)) : List.of());

        List<Row> page = source.reader().read(row -> true, null, 1);

        assertEquals(List.of(new Row(4_990, 4_990)), page);
        assertEquals(10, source.calls.size(), "5,000 source objects in pages of 500, not 4,991 calls");
    }

    @Test
    void negativeOrMissingLimitReturnsEveryRowFromStart() {
        Source source = new Source(1_200, KfAgentExpandedPagedReaderTest::two);
        assertEquals(2_400, source.reader().read(row -> true, null, -1).size());
        assertEquals(2_395, source.reader().read(row -> true, 5, null).size());
        assertEquals(List.of(), source.reader().read(row -> true, 0, 0), "limit=0 -> []");
    }

    @Test
    void incompleteWholeScanIsReported() {
        Source source = new Source(600, KfAgentExpandedPagedReaderTest::two);
        source.reportedTotal = 700;
        assertThrows(NativeImportException.class, () -> source.reader().read(row -> true, null, null));
    }

    @Test
    void fullSnapshotOnlyForNoFiltersNoStartNoLimit() {
        Map<String, String> none = new LinkedHashMap<>();
        assertTrue(KfAgentExpandedPagedReader.isFullSnapshot(null, null, null));
        assertTrue(KfAgentExpandedPagedReader.isFullSnapshot(none, null, null), "the handler passes an empty map");
        assertTrue(KfAgentExpandedPagedReader.isFullSnapshot(none, 0, -1), "start=0 / limit=-1 mean the same");
        assertFalse(KfAgentExpandedPagedReader.isFullSnapshot(Map.of("source_id", "x"), null, null));
        assertFalse(KfAgentExpandedPagedReader.isFullSnapshot(none, 1, null));
        assertFalse(KfAgentExpandedPagedReader.isFullSnapshot(none, null, 0));
    }
}
