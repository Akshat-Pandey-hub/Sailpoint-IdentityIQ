package com.keyforge.iiq.workgroupmember;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the pure parsing of the live Edit-Workgroup members grid response (as captured
 * from {@code workgroupMembersDataSource.json}) and the JSF ViewState reader — no network.
 */
class WorkgroupMemberServiceTest {

    // Exactly the shape returned live for the AdminCap workgroup.
    private static final String MEMBERS_JSON =
            "{\"totalCount\":1,\"workgroupMembers\":[{\"id\":\"7f000101971416688197147684ad00ff\","
            + "\"name\":\"spadmin\",\"firstname\":\"Molly\",\"lastname\":\"J\"}]}";

    private static WorkgroupMemberService svc() {
        return new WorkgroupMemberService(null); // client not used by the pure parsers
    }

    @Test
    void parsesMembersAndTotal() {
        List<WorkgroupMembership> members = svc().parseMembers(MEMBERS_JSON, "WG-1");
        assertEquals(1, members.size());
        WorkgroupMembership m = members.get(0);
        assertEquals("WG-1", m.workgroupId());
        assertEquals("7f000101971416688197147684ad00ff", m.identityId());
        assertEquals("spadmin", m.memberName());
        assertEquals("Molly", m.firstName());
        assertEquals("J", m.lastName());
        assertEquals(1, svc().parseTotal(MEMBERS_JSON));
    }

    @Test
    void emptyGridYieldsNoMembers() {
        List<WorkgroupMembership> members = svc().parseMembers("{\"totalCount\":0,\"workgroupMembers\":[]}", "WG-2");
        assertTrue(members.isEmpty());
        assertEquals(0, svc().parseTotal("{\"totalCount\":0,\"workgroupMembers\":[]}"));
    }

    @Test
    void memberWithoutIdIsSkipped() {
        String json = "{\"totalCount\":1,\"workgroupMembers\":[{\"name\":\"noId\"}]}";
        assertTrue(svc().parseMembers(json, "WG-3").isEmpty());
    }

    @Test
    void readsJsfViewState() {
        String html = "<input type=\"hidden\" name=\"javax.faces.ViewState\" "
                + "id=\"j_id1:javax.faces.ViewState:0\" value=\"vs-abc:123\" />";
        assertEquals("vs-abc:123", WorkgroupMemberService.parseViewState(html));
        assertNull(WorkgroupMemberService.parseViewState("<html>no viewstate</html>"));
    }
}
