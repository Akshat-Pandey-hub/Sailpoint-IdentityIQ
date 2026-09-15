package com.keyforge.iiq.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.Identity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the SCIM User → {@code usr} migration mapping: correct source→column
 * values and types, COMPLETE preservation of the emails array (both values + type/
 * primary), extension arrays (capabilities), numeric riskScore, NULL when absent, and
 * a deterministic id. No {@code customattributes} dump exists any more.
 */
class UserRowMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SP_EXT = "urn:ietf:params:scim:schemas:sailpoint:1.0:User";
    private static final String ENT_EXT = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private static Identity identity(String id, String userName, String displayName, Boolean active,
                                     String email, String firstName, String lastName, ObjectNode extended) {
        return new Identity(id, userName, displayName, active, email, firstName, lastName,
                List.of(), List.of(), extended);
    }

    /** Builds the extendedAttributes as the extraction would (the complete raw remainder). */
    private static ObjectNode molly() {
        ObjectNode raw = MAPPER.createObjectNode();
        raw.putObject("name").put("formatted", "Molly J").put("givenName", "Molly").put("familyName", "J");
        raw.putArray("emails")
                .addObject().put("type", "work").put("value", "MoliJangada@vardhaman310.onmicrosoft.com").put("primary", true);
        raw.put("active", true);
        raw.putObject("meta").put("created", "2025-05-28T01:16:41.005Z")
                .put("lastModified", "2026-08-18T15:19:04.278Z").put("version", "W/\"1\"");
        ObjectNode ext = raw.putObject(SP_EXT);
        ext.put("isManager", false).put("riskScore", 257).put("lastRefresh", "2026-08-15T20:07:24.895Z");
        ext.putArray("capabilities").add("SystemAdministrator").add("HelpDesk");
        raw.putObject(ENT_EXT); // empty enterprise extension
        return raw;
    }

    @Test
    void mapsEveryScalarFieldWithCorrectValueAndType() {
        Identity id = identity("7f000101971416688197147684ad00ff", "spadmin", "Molly J", true,
                "MoliJangada@vardhaman310.onmicrosoft.com", "Molly", "J", molly());

        UserRow row = UserRowMapper.map(id);

        assertEquals("7f000101-9714-1668-8197-147684ad00ff", row.userid());
        assertEquals("spadmin", row.username());
        assertEquals("Molly J", row.displayName());
        assertEquals("Molly J", row.formattedName());
        assertEquals("Molly", row.firstName());
        assertEquals("J", row.lastName());
        assertEquals("MoliJangada@vardhaman310.onmicrosoft.com", row.email()); // scalar primary
        assertEquals(Boolean.TRUE, row.active());       // real boolean, not an enum
        assertEquals(Integer.valueOf(257), row.riskScore());
        assertEquals(Boolean.FALSE, row.isManager());
        assertEquals(LocalDateTime.of(2025, 5, 28, 1, 16, 41, 5_000_000), row.createdAt());
        assertEquals(LocalDateTime.of(2026, 8, 18, 15, 19, 4, 278_000_000), row.modifiedAt());
    }

    @Test
    void preservesTheCompleteEmailsArrayNotJustOne() throws Exception {
        ObjectNode raw = molly();
        // A SECOND email must survive — never reduced to the primary alone.
        raw.withArray("emails").addObject().put("type", "home").put("value", "molly@home.com").put("primary", false);
        Identity id = identity("7f000101971416688197147684ad00ff", "spadmin", "Molly J", true,
                "MoliJangada@vardhaman310.onmicrosoft.com", "Molly", "J", raw);

        JsonNode emails = MAPPER.readTree(UserRowMapper.map(id).emailsJson());
        assertTrue(emails.isArray());
        assertEquals(2, emails.size());
        assertEquals("work", emails.get(0).path("type").asText());
        assertEquals(true, emails.get(0).path("primary").asBoolean());
        assertEquals("molly@home.com", emails.get(1).path("value").asText());
        assertEquals("home", emails.get(1).path("type").asText());
    }

    @Test
    void preservesCapabilitiesArrayCompletely() throws Exception {
        Identity id = identity("7f000101971416688197147684ad00ff", "spadmin", "Molly J", true,
                "a@b.com", "Molly", "J", molly());
        JsonNode caps = MAPPER.readTree(UserRowMapper.map(id).capabilitiesJson());
        assertTrue(caps.isArray());
        assertEquals(2, caps.size());
        assertEquals("SystemAdministrator", caps.get(0).asText());
    }

    @Test
    void leavesAbsentFieldsNullAndInventsNothing() {
        // Minimal identity: no extension, no emails, no department.
        ObjectNode raw = MAPPER.createObjectNode();
        Identity id = identity("7f000101971416688197147684ad00ff", "u", null, null, null, null, null, raw);

        UserRow row = UserRowMapper.map(id);
        assertNull(row.email());
        assertNull(row.emailsJson());
        assertNull(row.active());
        assertNull(row.department());
        assertNull(row.employeeId());
        assertNull(row.riskScore());
        assertNull(row.isManager());
        assertNull(row.capabilitiesJson());
        assertNull(row.enterpriseAttributesJson());
        assertNull(row.createdAt());
    }

    @Test
    void emptyEnterpriseExtensionIsNull() {
        Identity id = identity("7f000101971416688197147684ad00ff", "spadmin", "Molly J", true,
                "a@b.com", "Molly", "J", molly());
        assertNull(UserRowMapper.map(id).enterpriseAttributesJson()); // {} -> null, not "{}"
    }

    @Test
    void deterministicUserIdFromHexOrDashed() {
        assertEquals("7f000101-9714-1668-8197-147684ad00ff",
                UserRowMapper.toCanonicalUuid("7f000101971416688197147684ad00ff"));
        assertEquals("7f000101-9714-1668-8197-147684ad00ff",
                UserRowMapper.toCanonicalUuid("7f000101-9714-1668-8197-147684ad00ff"));
    }

    @Test
    void invalidIdIsRejectedNotAltered() {
        ObjectNode raw = MAPPER.createObjectNode();
        Identity id = identity("not-a-uuid", "u", "U", true, null, "U", "U", raw);
        assertThrows(UserMappingException.class, () -> UserRowMapper.map(id));
    }
}
