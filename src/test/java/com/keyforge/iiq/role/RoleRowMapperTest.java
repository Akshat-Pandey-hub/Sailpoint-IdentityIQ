package com.keyforge.iiq.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.iiq.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Role → {@code kf_role} mapping: scalars to columns, {@code type.name} to
 * role_type, owner id canonicalised, SCIM timestamps parsed, descriptions preserved as
 * JSON, and a missing id rejected (never invented).
 */
class RoleRowMapperTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static Role sample() throws Exception {
        return new Role(
                "7f0001019fa71124819fb63337a51c28", "Engineering-Base", "Engineering-Base",
                new Role.Type("business", "Business"), Boolean.TRUE,
                new Role.Ref("7f000101971416688197147684ad00ff", "http://host/Users/x", "Molly J"),
                M.readTree("[{\"locale\":\"en_US\",\"value\":\"Birthright role\"}]"),
                "2026-07-31T03:23:57.733Z", null, null,
                new Role.Meta("Role", "http://host/Roles/x",
                        "2026-07-31T03:23:57.733Z", "2026-08-04T17:29:36.045Z", "W/\"1\""),
                List.of(), List.of(), List.of());
    }

    @Test
    void mapsScalarsTypeOwnerAndTimestamps() throws Exception {
        RoleRow row = RoleRowMapper.map(sample());

        assertEquals("7f000101-9fa7-1124-819f-b63337a51c28", row.roleid());
        assertEquals("Engineering-Base", row.name());
        assertEquals("Engineering-Base", row.displayableName());
        assertEquals("business", row.roleType());
        assertEquals("Business", row.roleTypeDisplay());
        assertEquals(Boolean.TRUE, row.enabled());
        assertEquals("7f000101-9714-1668-8197-147684ad00ff", row.ownerId());
        assertEquals("Molly J", row.ownerDisplayName());
        assertTrue(row.descriptionsJson().contains("Birthright role"));
        assertEquals(2026, row.activationDate().getYear());
        assertEquals(2026, row.createdAt().getYear());
        assertEquals(8, row.modifiedAt().getMonthValue());
        // Absent values stay null (never invented).
        assertNull(row.deactivationDate());
        assertNull(row.classificationsJson());
    }

    @Test
    void missingIdIsRejected() {
        Role bad = new Role("not-a-uuid", "X", "X", null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of());
        assertThrows(RoleMappingException.class, () -> RoleRowMapper.map(bad));
    }

    @Test
    void foreignOwnerIdThatIsNotAUuidBecomesNullNotAnError() {
        Role r = new Role("7f0001019fa71124819fb63337a51c28", "X", "X", null, null,
                new Role.Ref("not-a-uuid", null, "Someone"), null, null, null, null, null,
                List.of(), List.of(), List.of());
        RoleRow row = RoleRowMapper.map(r);
        assertNull(row.ownerId());
        assertEquals("Someone", row.ownerDisplayName());
    }
}
