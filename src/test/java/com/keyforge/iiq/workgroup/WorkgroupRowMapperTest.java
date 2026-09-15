package com.keyforge.iiq.workgroup;

import com.keyforge.iiq.model.UserGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the Workgroup → {@code kf_workgroup} mapping. On the current live source only
 * id/name/description/modified are present, so owner/status/created/member_count must be
 * NULL (never invented). A second case proves those columns DO populate when the source
 * provides them, so the mapper is honest rather than hardcoded.
 */
class WorkgroupRowMapperTest {

    /** Shaped exactly like the live workgroupsDataSource.json record (AdminCap). */
    private static UserGroup liveWorkgroup() {
        return new UserGroup(
                "7f0001019f061fdc819f10581517110e", "AdminCap", "Workgroup", "spadminq",
                null,                      // owner: not in the live DataSource
                List.of(), false,          // members: not provided
                null, null,                // rule, status: absent
                new UserGroup.Meta(null, null, null, "6/28/26, 10:27 PM", null),
                null);
    }

    @Test
    void mapsVerifiedFieldsAndLeavesUnprovidedFieldsNull() {
        WorkgroupRow row = WorkgroupRowMapper.map(liveWorkgroup());

        assertEquals("7f000101-9f06-1fdc-819f-10581517110e", row.workgroupid());
        assertEquals("7f0001019f061fdc819f10581517110e", row.sourceId());
        assertEquals("AdminCap", row.name());
        assertEquals("spadminq", row.description());
        // modified parsed from the US grid format "6/28/26, 10:27 PM"
        assertEquals(2026, row.modifiedAt().getYear());
        assertEquals(6, row.modifiedAt().getMonthValue());
        assertEquals(28, row.modifiedAt().getDayOfMonth());
        // NOT provided by the current source -> NULL, never invented
        assertNull(row.ownerId());
        assertNull(row.ownerDisplayName());
        assertNull(row.status());
        assertNull(row.createdAt());
        assertNull(row.memberCount());
    }

    @Test
    void populatesOwnerStatusMembersWhenSourceProvidesThem() {
        UserGroup withExtras = new UserGroup(
                "7f0001019f061fdc819f10581517110e", "AdminCap", "Workgroup", "desc",
                new UserGroup.Ref("7f000101971416688197147684ad00ff", null, "Molly J"),
                List.of(new UserGroup.Ref("a", null, "m1"), new UserGroup.Ref("b", null, "m2")), true,
                null, "active",
                new UserGroup.Meta(null, null, "2026-06-01T00:00:00Z", "2026-06-28T22:27:00Z", null),
                null);

        WorkgroupRow row = WorkgroupRowMapper.map(withExtras);
        assertEquals("7f000101-9714-1668-8197-147684ad00ff", row.ownerId());
        assertEquals("Molly J", row.ownerDisplayName());
        assertEquals("active", row.status());
        assertEquals(Integer.valueOf(2), row.memberCount());
        assertEquals(2026, row.createdAt().getYear());
    }

    @Test
    void missingIdIsRejected() {
        UserGroup noId = new UserGroup(
                null, "AdminCap", "Workgroup", null, null, List.of(), false, null, null, null, null);
        assertThrows(WorkgroupMappingException.class, () -> WorkgroupRowMapper.map(noId));
    }
}
