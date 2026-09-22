package com.keyforge.nativeiiq.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure model behavior (no SailPoint dependency). */
class NativeIdentityRowTest {

    @Test
    void holdsBasicAndRelationshipFields() {
        NativeIdentityRow r = new NativeIdentityRow();
        r.setSourceId("id-1");
        r.setName("jsmith");
        r.setEmail("j@x.com");
        r.setInactive(Boolean.FALSE);
        r.setManagerId("mgr-1");
        r.setManagerName("Boss");
        r.setType("employee");
        r.setCorrelated(Boolean.TRUE);
        r.setManagerStatus(Boolean.TRUE);
        r.setAdministratorId("adm-1");
        r.getAccounts().add(new NativeAccountRef("AD", "cn=jsmith", "east", "John Smith"));
        r.getAssignedRoles().add("Engineer");
        r.getDetectedRoles().add("Contractor");
        r.getRoleAssignments().add(new NativeRoleAssignmentRef("Engineer", "role-1", "granted"));
        r.getRoleDetections().add(new NativeRoleDetectionRef("Contractor", "role-2", null, "a1"));
        r.getCapabilities().add("SystemAdministrator");
        r.getControlledScopes().add("US");
        r.getAttributes().put("department", "IT");

        assertEquals("id-1", r.getSourceId());
        assertEquals("jsmith", r.getName());
        assertEquals("mgr-1", r.getManagerId());
        assertEquals("employee", r.getType());
        assertEquals(Boolean.TRUE, r.getCorrelated());
        assertEquals(Boolean.TRUE, r.getManagerStatus());
        assertEquals("adm-1", r.getAdministratorId());
        assertEquals(1, r.getAccounts().size());
        assertEquals("AD", r.getAccounts().get(0).getApplicationName());
        assertEquals("east", r.getAccounts().get(0).getInstance());
        assertEquals("John Smith", r.getAccounts().get(0).getDisplayName());
        assertEquals("Engineer", r.getAssignedRoles().get(0));
        assertEquals("Contractor", r.getDetectedRoles().get(0));
        assertEquals("role-1", r.getRoleAssignments().get(0).getRoleId());
        assertEquals("role-2", r.getRoleDetections().get(0).getRoleId());
        assertEquals("SystemAdministrator", r.getCapabilities().get(0));
        assertEquals("US", r.getControlledScopes().get(0));
        assertEquals("IT", r.getAttributes().get("department"));
    }

    @Test
    void srcInterfaceIsFixedNativeTag() {
        NativeIdentityRow r = new NativeIdentityRow();
        assertEquals("native_iiq_java_api", r.getSrcInterface());
        assertEquals("sailpoint.object.Identity", r.getSrcObjectType());
        assertNull(r.getManagerId(), "manager empty until set");
        assertTrue(r.getAccounts().isEmpty());
    }
}
