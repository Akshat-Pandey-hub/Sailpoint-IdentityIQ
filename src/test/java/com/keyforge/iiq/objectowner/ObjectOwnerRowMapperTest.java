package com.keyforge.iiq.objectowner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Application;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies object→owner edge derivation: verified owners on Application/Role/Entitlement
 * become {@code kf_object_owner} rows; objects with no owner produce no row (never
 * invented). Entitlement owner is read from the preserved raw attributes.
 */
class ObjectOwnerRowMapperTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String OWNER_ID = "7f000101971416688197147684ad00ff";
    private static final String OWNER_CANON = "7f000101-9714-1668-8197-147684ad00ff";

    @Test
    void mapsApplicationOwner() {
        Application app = new Application("7f00010198421229819849c815b90bfc", "EntraAuth", "Entra",
                new Application.Owner(OWNER_ID, "http://host/Users/x", "Molly J"), List.of(), null);

        ObjectOwnerRow row = ObjectOwnerRowMapper.fromApplication(app).orElseThrow();
        assertEquals("Application", row.objectType());
        assertEquals("7f000101-9842-1229-8198-49c815b90bfc", row.objectId());
        assertEquals("EntraAuth", row.objectName());
        assertEquals("owner", row.ownershipRole());
        assertEquals(OWNER_CANON, row.ownerId());
        assertEquals("Molly J", row.ownerDisplayName());
    }

    @Test
    void mapsRoleOwner() {
        Role role = new Role("7f0001019fa71124819fb63337a51c28", "Engineering-Base", "Engineering-Base",
                null, Boolean.TRUE, new Role.Ref(OWNER_ID, null, "Molly J"),
                null, null, null, null, null, List.of(), List.of(), List.of());

        ObjectOwnerRow row = ObjectOwnerRowMapper.fromRole(role).orElseThrow();
        assertEquals("Role", row.objectType());
        assertEquals("7f000101-9fa7-1124-819f-b63337a51c28", row.objectId());
        assertEquals("Engineering-Base", row.objectName());
        assertEquals(OWNER_CANON, row.ownerId());
    }

    @Test
    void mapsEntitlementOwnerFromPreservedRawAttributes() {
        ObjectNode extra = M.createObjectNode();
        ObjectNode owner = extra.putObject("owner");
        owner.put("value", OWNER_ID);
        owner.put("displayName", "Molly J");
        Entitlement ent = new Entitlement("7f0001019842122981984a0000000001", "Admins", "Admins",
                "groups", Boolean.TRUE, "group", null, null, extra);

        ObjectOwnerRow row = ObjectOwnerRowMapper.fromEntitlement(ent).orElseThrow();
        assertEquals("Entitlement", row.objectType());
        assertEquals("Admins", row.objectName());
        assertEquals(OWNER_CANON, row.ownerId());
    }

    @Test
    void noOwnerYieldsNoEdge() {
        Application appNoOwner = new Application("7f00010198421229819849c815b90bfc", "X", "Y",
                null, List.of(), null);
        assertTrue(ObjectOwnerRowMapper.fromApplication(appNoOwner).isEmpty());

        // Entitlement with no owner in its preserved attributes (the live case: 0/37 owned).
        Entitlement entNoOwner = new Entitlement("7f0001019842122981984a0000000002", "E", "E",
                "groups", Boolean.TRUE, "group", null, null, M.createObjectNode());
        assertTrue(ObjectOwnerRowMapper.fromEntitlement(entNoOwner).isEmpty());
    }

    @Test
    void deterministicIdIsStable() {
        Application app = new Application("7f00010198421229819849c815b90bfc", "EntraAuth", "Entra",
                new Application.Owner(OWNER_ID, null, "Molly J"), List.of(), null);
        Optional<ObjectOwnerRow> a = ObjectOwnerRowMapper.fromApplication(app);
        Optional<ObjectOwnerRow> b = ObjectOwnerRowMapper.fromApplication(app);
        assertTrue(a.isPresent());
        assertEquals(a.get().ownerid(), b.get().ownerid());
        assertFalse(a.get().ownerid().isBlank());
    }
}
