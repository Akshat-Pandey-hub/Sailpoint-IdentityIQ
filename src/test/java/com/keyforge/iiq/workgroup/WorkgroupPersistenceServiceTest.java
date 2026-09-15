package com.keyforge.iiq.workgroup;

import com.keyforge.iiq.model.UserGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@code kf_workgroup} is populated from the Workgroup subset ONLY —
 * Populations and Groups (which the same extraction returns) are excluded, never
 * conflated. DB-free: exercises the {@code workgroupsOnly} selector directly.
 */
class WorkgroupPersistenceServiceTest {

    private static UserGroup of(String name, String type) {
        return new UserGroup("id-" + name, name, type, null, null, List.of(), false, null, null, null, null);
    }

    @Test
    void selectsOnlyWorkgroups() {
        List<UserGroup> mixed = List.of(
                of("AdminCap", "Workgroup"),
                of("SomePopulation", "Population"),
                of("SomeGroup", "Group"),
                of("Adminq", "Workgroup"));

        List<UserGroup> workgroups = WorkgroupPersistenceService.workgroupsOnly(mixed);

        assertEquals(2, workgroups.size());
        assertTrue(workgroups.stream().allMatch(g -> "Workgroup".equals(g.getType())));
    }

    @Test
    void emptyOrNullInputYieldsNoWorkgroups() {
        assertTrue(WorkgroupPersistenceService.workgroupsOnly(List.of()).isEmpty());
        assertTrue(WorkgroupPersistenceService.workgroupsOnly(null).isEmpty());
    }
}
