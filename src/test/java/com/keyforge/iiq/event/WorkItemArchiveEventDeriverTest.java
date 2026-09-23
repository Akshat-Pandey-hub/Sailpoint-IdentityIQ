package com.keyforge.iiq.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkItemArchiveEventDeriverTest {
    @Test
    void derivesOneStableEventFromArchiveIdentityAndTimestamp() throws Exception {
        LocalDateTime archived = LocalDateTime.of(2026, 9, 1, 10, 11, 12);
        EventDeriver.WorkItemArchiveSrc src = new EventDeriver.WorkItemArchiveSrc(
                "archive-1", "approval-1", "Approval", "Completed", "owner", Boolean.TRUE,
                "Target", "request-1", null, 2, archived);
        EventRow event = EventDeriver.workItemArchive(src, "run-1");
        assertEquals(EventDeriver.TYPE_WORKITEM_ARCHIVE, event.srcObjectType());
        assertEquals("archive-1", event.srcObjectId());
        assertEquals(EventType.WORKITEM_ARCHIVED, event.eventType());
        assertEquals(archived, event.srcEventTs());
        assertEquals(EventRow.SECOND, event.srcEventTsPrecision());
        assertEquals("native_iiq_java_api", event.srcInterface());
        assertTrue(event.eventDetailJson().contains("\"signOffCount\":2"));
        assertEquals(event.eventId(), EventDeriver.workItemArchive(src, "different-run").eventId());
        assertNotEquals(event.eventId(), EventDeriver.workItemArchive(new EventDeriver.WorkItemArchiveSrc(
                "archive-2", "approval-1", "Approval", "Completed", "owner", Boolean.TRUE,
                "Target", "request-1", null, 2, archived), "run-1").eventId());
    }
}
