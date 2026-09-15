package com.keyforge.iiq.workitem;

import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.model.WorkItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link WorkItemService} POSTs to the captured endpoint, parses the IIQ REST
 * envelope ({@code objects[] + count + complete}) into {@link WorkItem}s, paginates,
 * and reads references — using a fake client (no network).
 */
class WorkItemServiceTest {

    private static final String WI_40 =
            "{\"id\":\"7f0001019fbf1a17819fcd8d067c1c33\",\"workItemName\":\"0000000040\","
            + "\"workItemType\":\"ManualAction\",\"created\":1785859999356,\"priority\":\"Normal\","
            + "\"commentCount\":0,\"editable\":true,"
            + "\"owner\":{\"id\":\"7f000101971416688197147684ad00ff\",\"name\":\"spadmin\",\"displayName\":\"Molly J\"},"
            + "\"requester\":{\"id\":\"7f000101971416688197147684ad00ff\",\"name\":\"spadmin\",\"displayName\":\"Molly J\"},"
            + "\"target\":{\"id\":\"7f0001019f061fdc819f9cdef3bc0862\",\"name\":\"App_IDJ0001009\",\"displayName\":\"Liam Whitfield\"}}";
    private static final String WI_39 =
            "{\"id\":\"7f0001019fbf1a17819fcd8d03fd1c25\",\"workItemName\":\"0000000039\","
            + "\"workItemType\":\"ManualAction\",\"created\":1785859998717,\"priority\":\"Normal\"}";

    @Test
    void parsesEnvelopeObjectsIntoWorkItems() {
        IiqApiClient fake = new IiqApiClient() {
            @Override
            public String postJson(String path, String body) {
                assertEquals(WorkItemService.WORK_ITEMS_PATH, path);
                return "{\"status\":\"success\",\"objects\":[" + WI_40 + "," + WI_39 + "],"
                        + "\"count\":2,\"complete\":true}";
            }
        };

        List<WorkItem> items = new WorkItemService(fake).getAllWorkItems();

        assertEquals(2, items.size());
        WorkItem first = items.get(0);
        assertEquals("0000000040", first.getWorkItemName());
        assertEquals("ManualAction", first.getWorkItemType());
        assertEquals("Molly J", first.getOwner().getDisplayName());
        assertEquals("App_IDJ0001009", first.getTarget().getName());
        assertEquals("Liam Whitfield", first.getTarget().getDisplayName());
        assertEquals(Long.valueOf(1785859999356L), first.getCreated());
    }

    @Test
    void sendsStartAndLimitInTheRequestBody() {
        final String[] captured = new String[1];
        IiqApiClient fake = new IiqApiClient() {
            @Override
            public String postJson(String path, String body) {
                captured[0] = body;
                return "{\"status\":\"success\",\"objects\":[" + WI_39 + "],\"count\":1,\"complete\":true}";
            }
        };

        new WorkItemService(fake).getAllWorkItems();

        assertTrue(captured[0].contains("\"start\":0"));
        assertTrue(captured[0].contains("\"limit\":"));
        assertTrue(captured[0].contains("workItemName")); // the sort clause the page sends
    }

    @Test
    void paginatesUntilAllRowsRead() {
        IiqApiClient paging = new IiqApiClient() {
            @Override
            public String postJson(String path, String body) {
                // Page 1 (start:0) returns 2 of 3, not complete; page 2 returns the third.
                if (body.contains("\"start\":0")) {
                    return "{\"status\":\"success\",\"objects\":[" + WI_40 + "," + WI_39 + "],"
                            + "\"count\":3,\"complete\":false}";
                }
                return "{\"status\":\"success\",\"objects\":[" + WI_39 + "],\"count\":3,\"complete\":true}";
            }
        };

        assertEquals(3, new WorkItemService(paging).getAllWorkItems().size());
    }

    @Test
    void emptyResultYieldsNoWorkItems() {
        IiqApiClient fake = new IiqApiClient() {
            @Override
            public String postJson(String path, String body) {
                return "{\"status\":\"success\",\"objects\":[],\"count\":0,\"complete\":true}";
            }
        };
        assertEquals(0, new WorkItemService(fake).getAllWorkItems().size());
    }
}
