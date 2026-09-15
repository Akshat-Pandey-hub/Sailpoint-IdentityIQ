package com.keyforge.iiq.accessrequest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses the real ui/rest/identityRequests envelope shape and maps request/item/approval rows.
 * Verifies canonical ids, epoch→timestamp, item/approval fan-out, deterministic approval id,
 * the authoritative WorkItem/archive linkage fields, and empty-source behavior.
 */
class AccessRequestTest {

    private static final String JSON =
            "{\"objects\":[{\"type\":\"Lifecycle\",\"targetDisplayName\":\"Liam Whitfield\","
            + "\"requesterDisplayName\":\"Molly J\",\"requestId\":\"0000000021\","
            + "\"id\":\"7f0001019fbf1a17819fcd9791db1c96\",\"priority\":\"Normal\","
            + "\"executionStatus\":\"Completed\",\"state\":\"End\",\"createdDate\":1786824451331,"
            + "\"endDate\":1786824451787,\"terminatedDate\":null,\"cancelable\":false,"
            + "\"items\":[{\"id\":\"7f0001019fbf1a17819fcd9791d91c93\",\"operation\":\"Remove\","
            + "\"applicationName\":\"IdentityIQ\",\"role\":true,\"entitlement\":false,"
            + "\"hasManagedAttribute\":false,\"accountName\":\"App_IDJ0001009\",\"name\":\"assignedRoles\","
            + "\"value\":\"IT Operations Employee\",\"displayableValue\":\"IT Operations Employee\","
            + "\"approvalState\":null,\"provisioningState\":\"Finished\",\"provisioningEngine\":\"IdentityIQ\","
            + "\"assignmentId\":\"e058f168a27f4a34aeb644f7b7cd150c\",\"retries\":0}],"
            + "\"interactions\":[{\"workItemId\":null,\"ownerDisplayName\":\"Molly J\",\"comments\":null,"
            + "\"openDate\":1785860690703,\"completeDate\":1785860830021,\"status\":\"work_item_state_finished\","
            + "\"approvalItemCount\":1,\"description\":\"Manual Changes requested for User: Liam Whitfield\","
            + "\"workItemArchiveId\":null,\"workItemName\":null}]}],\"count\":45}";

    private static AccessRequestService svc() {
        return new AccessRequestService(null); // client unused by the pure parser
    }

    @Test
    void parsesRequestWithItemsAndInteractions() {
        List<AccessRequest> rs = svc().parseRequests(JSON);
        assertEquals(1, rs.size());
        AccessRequest r = rs.get(0);
        assertEquals("0000000021", r.requestId());
        assertEquals(1, r.items().size());
        assertEquals(1, r.interactions().size());
    }

    @Test
    void mapsRequestRow() {
        AccessRequest r = svc().parseRequests(JSON).get(0);
        AccessRequestRow row = AccessRequestRowMapper.mapRequest(r);
        assertEquals("7f000101-9fbf-1a17-819f-cd9791db1c96", row.requestid());
        assertEquals("0000000021", row.requestNumber());
        assertEquals("Lifecycle", row.type());
        assertEquals("Completed", row.executionStatus());
        assertEquals(Integer.valueOf(1), row.itemCount());
        assertEquals(2026, row.createdAt().getYear());
        assertEquals(Boolean.FALSE, row.cancelable());
    }

    @Test
    void mapsItemRowWithParentAndFlags() {
        AccessRequest r = svc().parseRequests(JSON).get(0);
        RequestItemRow it = AccessRequestRowMapper.mapItem(r, r.items().get(0));
        assertEquals("7f000101-9fbf-1a17-819f-cd9791d91c93", it.itemid());
        assertEquals("7f000101-9fbf-1a17-819f-cd9791db1c96", it.requestid());
        assertEquals("0000000021", it.requestNumber());
        assertEquals("Remove", it.operation());
        assertEquals("IT Operations Employee", it.value());
        assertEquals(Boolean.TRUE, it.isRole());
        assertEquals("Finished", it.provisioningState());
        assertEquals("e058f168a27f4a34aeb644f7b7cd150c", it.assignmentId());
    }

    @Test
    void mapsApprovalRowWithDeterministicIdAndWorkItemLink() {
        AccessRequest r = svc().parseRequests(JSON).get(0);
        RequestApprovalRow a = AccessRequestRowMapper.mapApproval(r, r.interactions().get(0), 0);
        assertFalse(a.id().isBlank());
        assertEquals("7f000101-9fbf-1a17-819f-cd9791db1c96", a.requestid());
        assertEquals("Molly J", a.ownerDisplayName());
        assertEquals("work_item_state_finished", a.status());
        assertEquals(Integer.valueOf(1), a.approvalItemCount());
        assertNull(a.workItemId());          // open work item id absent (completed)
        assertNull(a.workItemArchiveId());   // archive id null in this sample
        assertEquals(2026, a.openDate().getYear());
        // deterministic id stable
        assertEquals(a.id(), AccessRequestRowMapper.mapApproval(r, r.interactions().get(0), 0).id());
    }

    @Test
    void emptyEnvelopeYieldsNoRequests() {
        assertTrue(svc().parseRequests("{\"objects\":[],\"count\":0}").isEmpty());
    }
}
