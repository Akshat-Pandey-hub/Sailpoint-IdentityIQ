package com.keyforge.iiq.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Empty-source safety: with no source rows the deriver produces no events (and never errors). Also
 * confirms the aggregate deriver simply concatenates the per-source events, so a run over a
 * partially-populated set yields exactly the events for the populated sources.
 */
class EventEmptySourceTest {

    @Test
    void allEmptySourcesYieldNoEvents() {
        List<EventRow> rows = EventDeriver.deriveAll(List.of(), List.of(), List.of(), List.of(), "run");
        assertTrue(rows.isEmpty());
    }

    @Test
    void nullSourcesAreTreatedAsEmpty() {
        List<EventRow> rows = EventDeriver.deriveAll(null, null, null, null, "run");
        assertTrue(rows.isEmpty());
    }

    @Test
    void onlyPopulatedSourcesContributeEvents() {
        List<EventRow> rows = EventDeriver.deriveAll(
                List.of(),
                List.of(new EventDeriver.TaskSrc("t1", "Success",
                        LocalDateTime.of(2026, 9, 1, 1, 0), LocalDateTime.of(2026, 9, 1, 2, 0))),
                List.of(),
                List.of(),
                "run");
        assertEquals(1, rows.size());
        assertEquals(EventDeriver.TYPE_TASK_RESULT, rows.get(0).srcObjectType());
    }
}
