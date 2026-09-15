package com.keyforge.iiq.entitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Entitlement;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Entitlement → {@code entitlement} mapping: every source field lands in
 * its own typed column ({@code requestable} is NOT lost), {@code type} is the raw
 * source string (posixgroup stays posixgroup — no enum coercion), and {@code schemas}
 * is preserved as an array.
 */
class EntitlementRowMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENT_HEX = "7f0001019eb91d3c819f020fe42b7f45";
    private static final String ENT_CANON = "7f000101-9eb9-1d3c-819f-020fe42b7f45";
    private static final String APP_HEX = "7f0001019eb91d3c819f01f0c4127efb";
    private static final String APP_CANON = "7f000101-9eb9-1d3c-819f-01f0c4127efb";

    private static Entitlement entitlement(String type, Boolean aggregated, ObjectNode extra) {
        ObjectNode e = extra == null ? MAPPER.createObjectNode() : extra;
        if (aggregated != null) {
            e.put("aggregated", aggregated);
        }
        Entitlement.ApplicationRef app = new Entitlement.ApplicationRef(
                "corp directory-LDAP-Target", APP_HEX, "https://iiq/Applications/" + APP_HEX);
        return new Entitlement(ENT_HEX, "devs", "cn=devs,ou=rocktar,dc=moli,dc=org", "posixgroups",
                true, type, app,
                new Entitlement.Meta("Entitlement", "https://iiq/x", "2026-06-26T03:53:43.723Z",
                        "2026-08-03T19:12:06.260Z", "W/\"1\""),
                e);
    }

    @Test
    void mapsAllFieldsIncludingRequestable() {
        EntitlementRow row = EntitlementRowMapper.map(entitlement("posixgroup", true, null), Set.of(APP_CANON));

        assertEquals(ENT_CANON, row.entitlementid());
        assertEquals("cn=devs,ou=rocktar,dc=moli,dc=org", row.value());
        assertEquals("devs", row.displayableName());
        assertEquals("posixgroup", row.type());          // raw source string, NOT an enum
        assertEquals("posixgroups", row.attribute());
        assertEquals(Boolean.TRUE, row.aggregated());
        assertEquals(Boolean.TRUE, row.requestable());   // NOT lost
        assertEquals(APP_CANON, row.instanceid());       // application relationship resolved
        assertEquals("corp directory-LDAP-Target", row.applicationDisplayName());
        assertEquals(LocalDateTime.of(2026, 6, 26, 3, 53, 43, 723_000_000), row.createdAt());
    }

    @Test
    void preservesSchemasArray() throws Exception {
        ObjectNode extra = MAPPER.createObjectNode();
        extra.putArray("schemas").add("urn:ietf:params:scim:schemas:sailpoint:1.0:Entitlement");
        extra.put("lastRefresh", "2026-08-03T19:12:06.260Z");

        EntitlementRow row = EntitlementRowMapper.map(entitlement("group", false, extra), Set.of(APP_CANON));
        JsonNode schemas = MAPPER.readTree(row.schemasJson());
        assertTrue(schemas.isArray());
        assertEquals(1, schemas.size());
        assertEquals(LocalDateTime.of(2026, 8, 3, 19, 12, 6, 260_000_000), row.lastRefresh());
    }

    @Test
    void instanceIdNullWhenApplicationNotAKnownInstance() {
        EntitlementRow row = EntitlementRowMapper.map(entitlement("group", false, null), Set.of());
        assertNull(row.instanceid());
        assertEquals("corp directory-LDAP-Target", row.applicationDisplayName());
    }

    @Test
    void nullSchemasAndAggregatedWhenAbsent() {
        Entitlement e = new Entitlement(ENT_HEX, "devs", "cn=devs", "posixgroups", true, "group", null);
        EntitlementRow row = EntitlementRowMapper.map(e, Set.of());
        assertNull(row.schemasJson());
        assertNull(row.aggregated());
        assertNull(row.instanceid());
    }

    @Test
    void invalidIdIsRejected() {
        Entitlement e = new Entitlement("bad", "d", "v", "groups", true, "group", null);
        assertThrows(EntitlementMappingException.class, () -> EntitlementRowMapper.map(e, Set.of()));
    }
}
