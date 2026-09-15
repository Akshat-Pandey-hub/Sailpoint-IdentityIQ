package com.keyforge.iiq.objectowner;

import com.keyforge.iiq.model.Application;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies aggregation across sources: only objects that actually have an owner become
 * edges (DB-free, via {@code buildRows}). Mirrors the live instance where Applications and
 * Roles are owned but Entitlements are not.
 */
class ObjectOwnerPersistenceServiceTest {

    private static final String OWNER = "7f000101971416688197147684ad00ff";

    @Test
    void buildsEdgesOnlyForOwnedObjects() {
        List<Application> apps = List.of(
                new Application("7f00010198421229819849c815b90bfc", "EntraAuth", "Entra",
                        new Application.Owner(OWNER, null, "Molly J"), List.of(), null),
                new Application("7f00010198421229819849c815b90bfd", "NoOwnerApp", "Entra",
                        null, List.of(), null));                       // no owner -> no edge
        List<Role> roles = List.of(
                new Role("7f0001019fa71124819fb63337a51c28", "Engineering-Base", "Engineering-Base",
                        null, Boolean.TRUE, new Role.Ref(OWNER, null, "Molly J"),
                        null, null, null, null, null, List.of(), List.of(), List.of()));
        // Entitlement with no owner (the live 0/37 case) -> no edge.
        List<Entitlement> ents = List.of(
                new Entitlement("7f0001019842122981984a0000000001", "E", "E", "groups",
                        Boolean.TRUE, "group", null, null, null));

        List<ObjectOwnerRow> rows = ObjectOwnerPersistenceService.buildRows(apps, roles, ents);

        assertEquals(2, rows.size());
        assertEquals(1, rows.stream().filter(r -> "Application".equals(r.objectType())).count());
        assertEquals(1, rows.stream().filter(r -> "Role".equals(r.objectType())).count());
        assertEquals(0, rows.stream().filter(r -> "Entitlement".equals(r.objectType())).count());
    }
}
