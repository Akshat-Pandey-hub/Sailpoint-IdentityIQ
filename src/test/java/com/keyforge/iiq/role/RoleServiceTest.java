package com.keyforge.iiq.role;

import com.keyforge.iiq.client.IiqApiClient;
import com.keyforge.iiq.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link RoleService} parses the live SCIM Role shape (as captured from
 * {@code /scim/v2/Roles}) into {@link Role}s: scalar fields, the {@code type} object,
 * {@code owner}, and the role→role arrays — using a fake client (no network).
 */
class RoleServiceTest {

    // Shaped exactly like the captured live payload (Engineering-Base), with an added
    // second role carrying inheritance/permits to exercise the relationship parsing.
    private static final String ROLE_A =
            "{\"id\":\"7f0001019fa71124819fb63337a51c28\",\"name\":\"Engineering-Base\","
            + "\"displayableName\":\"Engineering-Base\","
            + "\"type\":{\"name\":\"business\",\"displayName\":\"Business\",\"permits\":true,\"requirements\":true},"
            + "\"active\":true,"
            + "\"owner\":{\"displayName\":\"Molly J\",\"value\":\"7f000101971416688197147684ad00ff\","
            + "\"$ref\":\"http://host/identityiq/scim/v2/Users/7f000101971416688197147684ad00ff\"},"
            + "\"descriptions\":[{\"locale\":\"en_US\",\"value\":\"Birthright role for Engineering employees\"}],"
            + "\"meta\":{\"created\":\"2026-07-31T03:23:57.733Z\",\"lastModified\":\"2026-08-04T17:29:36.045Z\","
            + "\"resourceType\":\"Role\"}}";
    private static final String ROLE_B =
            "{\"id\":\"7f0001019fbf1a17819fc7e670310ee7\",\"name\":\"IT Operations Employee\","
            + "\"displayableName\":\"IT Operations Employee\",\"type\":{\"name\":\"business\",\"displayName\":\"Business\"},"
            + "\"active\":true,"
            + "\"inheritance\":[{\"value\":\"7f0001019fbf1a17819fc7e6276a0ee5\",\"displayName\":\"HR Employee\","
            + "\"$ref\":\"http://host/identityiq/scim/v2/Roles/7f0001019fbf1a17819fc7e6276a0ee5\"}],"
            + "\"permits\":[{\"value\":\"7f0001019fa71124819fb63337a51c28\",\"displayName\":\"Engineering-Base\"}]}";

    private static IiqApiClient fakeReturning(String listResponse) {
        return new IiqApiClient() {
            @Override
            public String get(String path, Map<String, String> query) {
                assertEquals(RoleService.ROLES_PATH, path);
                return listResponse;
            }
        };
    }

    @Test
    void parsesRolesWithTypeOwnerAndTimestamps() {
        String body = "{\"totalResults\":2,\"Resources\":[" + ROLE_A + "," + ROLE_B + "]}";
        List<Role> roles = new RoleService(fakeReturning(body)).getAllRoles();

        assertEquals(2, roles.size());
        Role a = roles.get(0);
        assertEquals("Engineering-Base", a.getDisplayableName());
        assertNotNull(a.getType());
        assertEquals("business", a.getType().getName());
        assertEquals("Business", a.getType().getDisplayName());
        assertEquals(Boolean.TRUE, a.getActive());
        assertNotNull(a.getOwner());
        assertEquals("Molly J", a.getOwner().getDisplayName());
        assertEquals("7f000101971416688197147684ad00ff", a.getOwner().getValue());
        assertNotNull(a.getDescriptions());
        assertEquals("2026-07-31T03:23:57.733Z", a.getMeta().getCreated());
        // no hierarchy on role A
        assertTrue(a.getInheritance().isEmpty());
        assertTrue(a.getPermits().isEmpty());
    }

    @Test
    void parsesRoleRelationshipArrays() {
        String body = "{\"totalResults\":1,\"Resources\":[" + ROLE_B + "]}";
        Role b = new RoleService(fakeReturning(body)).getAllRoles().get(0);

        assertEquals(1, b.getInheritance().size());
        assertEquals("7f0001019fbf1a17819fc7e6276a0ee5", b.getInheritance().get(0).getValue());
        assertEquals("HR Employee", b.getInheritance().get(0).getDisplayName());
        assertEquals(1, b.getPermits().size());
        assertEquals("Engineering-Base", b.getPermits().get(0).getDisplayName());
        assertTrue(b.getRequirements().isEmpty());
    }

    @Test
    void emptyResultYieldsNoRoles() {
        List<Role> roles = new RoleService(fakeReturning("{\"totalResults\":0,\"Resources\":[]}")).getAllRoles();
        assertEquals(0, roles.size());
        assertNull(roles.stream().findFirst().orElse(null));
    }
}
