package com.keyforge.iiq.application;

import com.keyforge.iiq.model.Application;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the Application → {@code applicationinstance} mapping: instance identity,
 * the FK to {@code application}, and the owner resolved to a {@code usr} — with no
 * {@code configuration} column and no invented values.
 */
class ApplicationInstanceRowMapperTest {

    private static final String APP_ID_HEX = "7f00010198421229819849c815b90bfc";
    private static final String APP_ID_CANON = "7f000101-9842-1229-8198-49c815b90bfc";
    private static final String OWNER_HEX = "7f000101971416688197147684ad00ff";
    private static final String OWNER_CANON = "7f000101-9714-1668-8197-147684ad00ff";

    private static Application.Owner owner(String display, String value) {
        return new Application.Owner(value, "https://iiq/Users/" + value, display);
    }

    @Test
    void mapsInstanceIdentityAndRelationships() {
        Application app = new Application(APP_ID_HEX, "EntraAuth", "Azure Active Directory",
                owner("Molly J", OWNER_HEX), List.of(),
                new Application.Meta("Application", "https://iiq/x", "2025-07-27T02:48:26Z",
                        "2025-07-27T04:13:13Z", "W/\"1\""));

        ApplicationInstanceRow row = ApplicationInstanceRowMapper.map(
                app, Set.of(APP_ID_CANON), Set.of(OWNER_CANON));

        assertEquals(APP_ID_CANON, row.instanceid());
        assertEquals(APP_ID_CANON, row.applicationid());      // application row exists
        assertEquals("EntraAuth", row.instancename());
        assertEquals("Azure Active Directory", row.appType());
        assertEquals(OWNER_CANON, row.ownerId());             // owner exists in usr
        assertEquals("Molly J", row.ownerDisplayName());
        assertEquals(LocalDateTime.of(2025, 7, 27, 2, 48, 26), row.createdAt());
    }

    @Test
    void applicationIdNullWhenApplicationRowAbsent() {
        Application app = new Application(APP_ID_HEX, "X", "JDBC", null, List.of(), null);
        ApplicationInstanceRow row = ApplicationInstanceRowMapper.map(app, Set.of(), Set.of());
        assertEquals(APP_ID_CANON, row.instanceid());
        assertNull(row.applicationid());
    }

    @Test
    void ownerNullWhenNotAKnownUser() {
        Application app = new Application(APP_ID_HEX, "X", "JDBC", owner("Ghost", OWNER_HEX), List.of(), null);
        ApplicationInstanceRow row = ApplicationInstanceRowMapper.map(app, Set.of(APP_ID_CANON), Set.of());
        assertNull(row.ownerId());
        assertEquals("Ghost", row.ownerDisplayName()); // display kept even when unresolved
    }

    @Test
    void toleratesMissingOptionalFields() {
        Application app = new Application(APP_ID_CANON, null, null, null, List.of(), null);
        ApplicationInstanceRow row = ApplicationInstanceRowMapper.map(app, Set.of(), Set.of());
        assertNull(row.instancename());
        assertNull(row.appType());
        assertNull(row.ownerId());
        assertNull(row.createdAt());
    }

    @Test
    void invalidIdIsRejected() {
        Application app = new Application("bad-id", "X", "JDBC", null, List.of(), null);
        assertThrows(ApplicationInstanceMappingException.class,
                () -> ApplicationInstanceRowMapper.map(app, Set.of(), Set.of()));
    }
}
