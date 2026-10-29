package com.keyforge.nativeload;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Only an empty page ends a read. An object deleted while IIQ loads a page leaves that page short and moves every later
 * object up one place; the read must carry on from what it received and still return every object that exists.
 */
class MidReadDeletionTest {

    /**
     * Behaves like a plugin endpoint over an ordered population: a page is found by offset, then each object is loaded.
     * {@code deleteWhileLoading} deletes that object between the two steps of the first page that contains it.
     */
    private static final class Plugin {
        final List<String> data = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        String deleteWhileLoading;

        Plugin(int size) {
            for (int i = 1; i <= size; i++) {
                data.add("r" + i);
            }
        }

        String fetch(int start, int limit) {
            calls.add(start + ":" + limit);
            List<String> found = new ArrayList<>(data.subList(Math.min(start, data.size()),
                    Math.min(data.size(), start + limit)));
            if (deleteWhileLoading != null && found.remove(deleteWhileLoading)) {
                data.remove(deleteWhileLoading); // found by the search, gone before it could be loaded
                deleteWhileLoading = null;
            }
            return String.join(",", found);
        }

        static List<String> parse(String body) {
            return body.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(body.split(",")));
        }

        KfAgentPagedReader<String> reader(int pageSize) {
            return new KfAgentPagedReader<>(this::fetch, Plugin::parse, pageSize, pageSize);
        }
    }

    private static List<String> range(int from, int to, String... except) {
        List<String> out = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            out.add("r" + i);
        }
        out.removeAll(Arrays.asList(except));
        return out;
    }

    @Test
    void aShortPageIsTopUppedWithTheNextExistingRows() {
        // 100 records, ask for 20, #19 is deleted while the page loads: the caller still gets 20 existing records
        Plugin plugin = new Plugin(100);
        plugin.deleteWhileLoading = "r19";
        List<String> page = plugin.reader(1000).window(0, 20);
        assertEquals(range(1, 21, "r19"), page);
        assertEquals(List.of("0:20", "19:1"), plugin.calls, "one extra call fetched the next existing record");

        // the caller's next page continues exactly after it: no gap, no repeat
        assertEquals(range(22, 41), plugin.reader(1000).window(20, 20));
    }

    @Test
    void aFullReadDoesNotStopAtAShortPageInTheMiddle() {
        Plugin plugin = new Plugin(1200);
        plugin.deleteWhileLoading = "r250";
        List<String> all = plugin.reader(500).filtered(r -> true, 0, null);
        assertEquals(range(1, 1200, "r250"), all, "every existing record, not just the first 499");
        assertEquals(List.of("0:500", "499:500", "999:500", "1199:500"), plugin.calls,
                "advance by what was received; the empty page at the end confirms the end");
    }

    @Test
    void atTheTrueEndOneEmptyCallConfirmsIt() {
        Plugin plugin = new Plugin(102);
        assertEquals(range(1, 102), plugin.reader(100).filtered(r -> true, 0, null));
        assertEquals(List.of("0:100", "100:100", "102:100"), plugin.calls);

        Plugin full = new Plugin(100);
        assertEquals(range(1, 100), full.reader(100).filtered(r -> true, 0, null));
        assertEquals(List.of("0:100", "100:100"), full.calls, "after a full page the next is read, as before; it is empty");
    }

    @Test
    void aFilteredReadKeepsMatchingAfterAShortPage() {
        Plugin plugin = new Plugin(30);
        plugin.deleteWhileLoading = "r3";
        List<String> evens = plugin.reader(10).filtered(r -> Integer.parseInt(r.substring(1)) % 2 == 0, 0, null);
        assertEquals(List.of("r2", "r4", "r6", "r8", "r10", "r12", "r14", "r16", "r18", "r20", "r22", "r24", "r26",
                "r28", "r30"), evens);
    }

    @Test
    void anExpandedReadCarriesOnAfterASourcePageThatLostAnObject() {
        // 6 source objects, each expanding to 2 rows; object 2 is deleted while the first page (of 3) loads
        List<Integer> objects = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6));
        boolean[] deleted = {false};
        List<String> calls = new ArrayList<>();
        KfAgentExpandedPagedReader<String> reader = new KfAgentExpandedPagedReader<>(
                (start, limit) -> {
                    calls.add(start + ":" + limit);
                    List<Integer> found = new ArrayList<>(objects.subList(Math.min(start, objects.size()),
                            Math.min(objects.size(), start + limit)));
                    if (!deleted[0] && found.remove(Integer.valueOf(2))) {
                        objects.remove(Integer.valueOf(2));
                        deleted[0] = true;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (Integer o : found) {
                        sb.append(sb.length() == 0 ? "" : ",").append(o);
                    }
                    return sb.toString();
                },
                (json, offset, requested) -> {
                    List<String> rows = new ArrayList<>();
                    int received = 0;
                    if (!json.isEmpty()) {
                        for (String o : json.split(",")) {
                            received++;
                            rows.add(o + "a");
                            rows.add(o + "b");
                        }
                    }
                    return new KfAgentExpandedPagedReader.Page<>(rows, offset + received, received < requested);
                }, 3);

        List<String> rows = reader.read(r -> true, 0, null);
        assertEquals(List.of("1a", "1b", "3a", "3b", "4a", "4b", "5a", "5b", "6a", "6b"), rows);
        assertEquals(List.of("0:3", "2:3", "5:3"), calls, "short source page, then on from what was received, then empty");
    }
}
