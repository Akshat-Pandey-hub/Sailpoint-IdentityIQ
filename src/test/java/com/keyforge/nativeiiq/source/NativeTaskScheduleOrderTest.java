package com.keyforge.nativeiiq.source;

import com.keyforge.nativeiiq.model.NativeTaskScheduleRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IIQ ignores the requested order for TaskSchedule, so the plugin fixes it (name, then id) before taking the page:
 * whatever order IIQ returns, every page is the same slice and paging never repeats or skips a schedule.
 */
class NativeTaskScheduleOrderTest {

    private static NativeTaskScheduleRow row(String name, String id) {
        NativeTaskScheduleRow r = new NativeTaskScheduleRow();
        r.setName(name);
        r.setSourceId(id);
        return r;
    }

    private static List<String> names(List<NativeTaskScheduleRow> rows) {
        List<String> out = new ArrayList<String>();
        for (NativeTaskScheduleRow r : rows) {
            out.add(r.getName() + "/" + r.getSourceId());
        }
        return out;
    }

    private static List<NativeTaskScheduleRow> schedules() {
        // the order live preview IIQ returned
        return new ArrayList<NativeTaskScheduleRow>(Arrays.asList(
                row("Check sunset requests for notifications daily", "s1"),
                row("Dispatch Access History daily", "s2"),
                row("Check expired work items daily", "s3"),
                row("Perform maintenance", "s4"),
                row("Perform Identity Request Maintenance", "s5"),
                row("Check expired mitigations daily", "s6")));
    }

    private static final List<String> SORTED = Arrays.asList(
            "Check expired mitigations daily/s6", "Check expired work items daily/s3",
            "Check sunset requests for notifications daily/s1", "Dispatch Access History daily/s2",
            "Perform Identity Request Maintenance/s5", "Perform maintenance/s4");

    @Test
    void sortsByNameThenIdWhateverOrderIiqReturns() {
        assertEquals(SORTED, names(NativeTaskScheduleExtractor.window(schedules(), 0, 0)));
        Random random = new Random(7);
        for (int i = 0; i < 20; i++) {
            List<NativeTaskScheduleRow> shuffled = schedules();
            Collections.shuffle(shuffled, random);
            assertEquals(SORTED, names(NativeTaskScheduleExtractor.window(shuffled, 0, 0)));
        }
    }

    @Test
    void pagesAreSlicesOfThatOrderAndNeverRepeatOrSkip() {
        List<String> paged = new ArrayList<String>();
        Random random = new Random(11);
        for (int start = 0; start < 6; start += 2) {
            List<NativeTaskScheduleRow> shuffled = schedules();
            Collections.shuffle(shuffled, random); // a different IIQ order on every call
            paged.addAll(names(NativeTaskScheduleExtractor.window(shuffled, start, 2)));
        }
        assertEquals(SORTED, paged);
        assertEquals(Arrays.asList("Perform maintenance/s4"), names(NativeTaskScheduleExtractor.window(schedules(), 5, 10)));
        assertEquals(new ArrayList<String>(), names(NativeTaskScheduleExtractor.window(schedules(), 9, 2)));
    }

    @Test
    void sameNameIsOrderedByIdAndMissingValuesGoLast() {
        List<NativeTaskScheduleRow> rows = new ArrayList<NativeTaskScheduleRow>(Arrays.asList(
                row("B", "2"), row(null, "1"), row("B", "1"), row("A", null), row("A", "9")));
        assertEquals(Arrays.asList("A/9", "A/null", "B/1", "B/2", "null/1"),
                names(NativeTaskScheduleExtractor.window(rows, 0, 0)));
    }
}
