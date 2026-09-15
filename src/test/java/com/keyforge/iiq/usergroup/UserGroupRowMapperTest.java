package com.keyforge.iiq.usergroup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keyforge.iiq.model.UserGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the pure source→row mapping: deterministic id (idempotency), source-only
 * fields, timestamp parsing, and complete raw preservation.
 */
class UserGroupRowMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static UserGroup group(String id, String name, String type, String description,
                                   UserGroup.Ref owner, List<UserGroup.Ref> members,
                                   boolean membersProvided, UserGroup.Meta meta) {
        ObjectNode raw = MAPPER.createObjectNode();
        raw.put("custom", "keep-me");
        if (id != null) {
            raw.put("id", id);
        }
        return new UserGroup(id, name, type, description, owner, members, membersProvided,
                null, null, meta, raw);
    }

    @Test
    void deterministicIdIsStableAcrossRuns() {
        UserGroup g = group("wg-1", "AdminCap", "Workgroup", null, null, List.of(), false, null);
        assertEquals(UserGroupRowMapper.map(g).id(), UserGroupRowMapper.map(g).id());
    }

    @Test
    void differentGroupsProduceDifferentIds() {
        String a = UserGroupRowMapper.map(group("wg-1", "A", "Workgroup", null, null, List.of(), false, null)).id();
        String b = UserGroupRowMapper.map(group("pop-1", "B", "Population", null, null, List.of(), false, null)).id();
        assertNotEquals(a, b);
    }

    @Test
    void mapsSourceFields() {
        UserGroup.Ref owner = new UserGroup.Ref(null, null, "spadminq");
        UserGroupRow row = UserGroupRowMapper.map(
                group("wg-1", "AttributeSyncWorkGroup", "Workgroup", "sync", owner, List.of(), false, null));
        assertEquals("Workgroup", row.sourceType());
        assertEquals("wg-1", row.sourceId());
        assertEquals("AttributeSyncWorkGroup", row.name());
        assertEquals("sync", row.description());
        assertEquals("spadminq", row.owner());
    }

    @Test
    void memberCountOnlyWhenMembersProvided() {
        UserGroup withMembers = group("wg-1", "Team", "Workgroup", null, null,
                List.of(new UserGroup.Ref("u-1", null, "Alice")), true, null);
        assertEquals(1, UserGroupRowMapper.map(withMembers).memberCount());

        UserGroup withoutMembers = group("wg-2", "Team2", "Workgroup", null, null, List.of(), false, null);
        assertNull(UserGroupRowMapper.map(withoutMembers).memberCount());
    }

    @Test
    void parsesEpochMillisAndIsoTimestampsElseNull() {
        UserGroup epoch = group("g", "N", "Group", null, null, List.of(), false,
                new UserGroup.Meta(null, null, null, "1719640620000", null));
        assertEquals(2024, UserGroupRowMapper.map(epoch).modifiedAt().getYear());

        UserGroup iso = group("g", "N", "Group", null, null, List.of(), false,
                new UserGroup.Meta(null, null, "2023-05-01T10:00:00Z", null, null));
        assertEquals(2023, UserGroupRowMapper.map(iso).createdAt().getYear());

        UserGroup bad = group("g", "N", "Group", null, null, List.of(), false,
                new UserGroup.Meta(null, null, "not-a-date", null, null));
        assertNull(UserGroupRowMapper.map(bad).createdAt());
    }

    @Test
    void everyGridFieldMapsToItsOwnColumn() {
        UserGroupRow row = UserGroupRowMapper.map(
                group("wg-1", "AdminCap", "Workgroup", "spadminq", null, List.of(), false, null));
        // There is no customattributes column at all; each field is a proper column.
        assertEquals("Workgroup", row.sourceType());
        assertEquals("wg-1", row.sourceId());
        assertEquals("AdminCap", row.name());
        assertEquals("spadminq", row.description());
    }

    @Test
    void missingIdAndNameIsRejected() {
        UserGroup g = group(null, null, "Workgroup", null, null, List.of(), false, null);
        assertThrows(UserGroupMappingException.class, () -> UserGroupRowMapper.map(g));
    }

    @Test
    void fallsBackToNameWhenNoId() {
        UserGroupRow row = UserGroupRowMapper.map(
                group(null, "OnlyName", "Group", null, null, List.of(), false, null));
        assertNull(row.sourceId());
        assertEquals("OnlyName", row.name());
        assertTrue(row.id().matches("[0-9a-f\\-]{36}"));
    }
}
