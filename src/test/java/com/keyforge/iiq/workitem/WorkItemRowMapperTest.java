package com.keyforge.iiq.workitem;

import com.keyforge.iiq.model.WorkItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the Work Item → {@code workitem} mapping: scalars to columns, references
 * flattened to id/name/displayName, epoch-millis to timestamps, ids canonicalised.
 */
class WorkItemRowMapperTest {

    private static WorkItem sample() {
        return new WorkItem(
                "7f0001019fbf1a17819fcd8d067c1c33", "0000000040", "ManualAction", null, null,
                "Manual Changes requested for User: Liam Whitfield", "Normal", 0, true,
                1785859999356L, null, null, null, 0, 0, null, null, null, false, false, false,
                new WorkItem.Ref("7f000101971416688197147684ad00ff", "spadmin", "Molly J"),
                new WorkItem.Ref("7f000101971416688197147684ad00ff", "spadmin", "Molly J"),
                null,
                new WorkItem.Ref("7f0001019f061fdc819f9cdef3bc0862", "App_IDJ0001009", "Liam Whitfield"));
    }

    @Test
    void mapsScalarsRefsAndTimestamps() {
        WorkItemRow row = WorkItemRowMapper.map(sample());

        assertEquals("7f000101-9fbf-1a17-819f-cd8d067c1c33", row.id());
        assertEquals("0000000040", row.workItemName());
        assertEquals("ManualAction", row.workItemType());
        assertEquals("Manual Changes requested for User: Liam Whitfield", row.description());
        assertEquals("Normal", row.priority());
        assertEquals(Integer.valueOf(0), row.commentCount());
        assertEquals(Boolean.TRUE, row.editable());

        // References flattened (id canonicalised + name + display name).
        assertEquals("7f000101-9714-1668-8197-147684ad00ff", row.ownerId());
        assertEquals("spadmin", row.ownerName());
        assertEquals("Molly J", row.ownerDisplayName());
        assertEquals("7f000101-9714-1668-8197-147684ad00ff", row.requesterId());
        assertEquals("7f000101-9f06-1fdc-819f-9cdef3bc0862", row.targetId());
        assertEquals("App_IDJ0001009", row.targetName());
        assertEquals("Liam Whitfield", row.targetDisplayName());

        // created epoch millis -> a real timestamp (2026).
        assertEquals(2026, row.createdAt().getYear());
        // absent reference / dates stay null (never invented).
        assertNull(row.assigneeId());
        assertNull(row.expirationDate());
        assertNull(row.wakeUpDate());
    }

    @Test
    void epochToUtcConvertsMillis() {
        assertNull(WorkItemRowMapper.epochToUtc(null));
        assertEquals(2026, WorkItemRowMapper.epochToUtc(1785859999356L).getYear());
    }

    @Test
    void missingIdIsRejected() {
        WorkItem bad = new WorkItem("not-a-uuid", "0001", "ManualAction", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
        assertThrows(WorkItemMappingException.class, () -> WorkItemRowMapper.map(bad));
    }
}
