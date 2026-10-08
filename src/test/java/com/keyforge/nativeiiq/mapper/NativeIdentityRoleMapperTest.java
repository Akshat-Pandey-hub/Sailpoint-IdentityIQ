package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeIdentityRoleRow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import sailpoint.object.Identity;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the native SailPoint-side wiring of the Identity-Role mapper against the real 8.4
 * {@code identityiq.jar}: the assignment-provenance getters inherited from {@code sailpoint.object.Assignment}
 * (assigner/date/startDate/endDate/source/negative/manual) land on the ASSIGNED row and are null on the
 * DETECTED row, and the RoleTarget JSON carries the new displayName/elevatedAccess keys. Self-skips when the
 * full IIQ runtime is not on the classpath (compiles only under the {@code native} profile).
 */
class NativeIdentityRoleMapperTest {

    @Test
    void assignedEdgeCarriesAssignmentProvenanceAndEnrichedTargets() {
        try {
            Identity id = new Identity();
            id.setId("id-1");
            id.setName("App_IDJ0001008");

            RoleAssignment ra = new RoleAssignment();
            ra.setId("asg-1");
            ra.setRoleId("role-1");
            ra.setRoleName("Engineering-Base");
            ra.setComments("granted by rule");
            ra.setAssigner("spadmin");
            ra.setSource("LCM");
            ra.setNegative(false);
            Date assignedDate = new Date(1759395600000L); // 2025-10-02T09:00:00Z-ish, fixed epoch
            ra.setDate(assignedDate);
            ra.setStartDate(assignedDate);
            ra.setEndDate(new Date(assignedDate.getTime() + 86400000L));

            RoleTarget target = new RoleTarget();
            target.setApplicationId("app-1");
            target.setApplicationName("Directory");
            target.setNativeIdentity("App_IDJ0001008");
            target.setRoleName("Engineering-Base");
            target.setDisplayName("Engineering Base");
            ra.setTargets(Collections.singletonList(target));

            id.setRoleAssignments(Collections.singletonList(ra));

            List<NativeIdentityRoleRow> out = new ArrayList<NativeIdentityRoleRow>();
            NativeIdentityRoleMapper.mapInto(id, out, "IdentityIQ", "run-1");

            assertEquals(1, out.size());
            NativeIdentityRoleRow row = out.get(0);
            assertEquals("ASSIGNED", row.getRelationshipType());
            assertEquals("spadmin", row.getAssigner());
            assertEquals("LCM", row.getSource());
            assertEquals(Boolean.FALSE, row.getNegative());
            assertNotNull(row.getManual(), "isManual() read verbatim (defaults to false on a fresh object)");
            assertEquals(assignedDate.toInstant(), row.getAssignedDate());
            assertEquals(assignedDate.toInstant(), row.getStartDate());
            assertNotNull(row.getEndDate());

            assertEquals(1, row.getTargets().size());
            Map<String, Object> t = row.getTargets().get(0);
            assertEquals("Directory", t.get("applicationName"));
            assertEquals("Engineering Base", t.get("displayName"));
            assertTrue(t.containsKey("elevatedAccess"), "RoleTarget.isElevatedAccess() wired into target JSON");
            assertNotNull(t.get("elevatedAccess"));
        } catch (LinkageError e) {
            Assumptions.abort("Requires the full IdentityIQ runtime; compiles against 8.4 identityiq.jar: " + e);
        }
    }

    @Test
    void detectedEdgeHasNullAssignmentProvenance() {
        try {
            Identity id = new Identity();
            id.setId("id-2");
            id.setName("Alexander Evans");

            RoleDetection rd = new RoleDetection();
            rd.setRoleId("role-2");
            rd.setRoleName("IT-Admin");
            rd.setAssignmentIds("a1,a2");
            rd.setDate(new Date(1756720800000L));
            id.setRoleDetections(Collections.singletonList(rd));

            List<NativeIdentityRoleRow> out = new ArrayList<NativeIdentityRoleRow>();
            NativeIdentityRoleMapper.mapInto(id, out, "IdentityIQ", "run-1");

            assertEquals(1, out.size());
            NativeIdentityRoleRow row = out.get(0);
            assertEquals("DETECTED", row.getRelationshipType());
            assertEquals("a1,a2", row.getDetectionAssignmentIds());
            assertNull(row.getAssigner(), "detected edge has no Assignment provenance");
            assertNull(row.getAssignedDate());
            assertNull(row.getStartDate());
            assertNull(row.getEndDate());
            assertNull(row.getSource());
            assertNull(row.getNegative());
            assertNull(row.getManual());
        } catch (LinkageError e) {
            Assumptions.abort("Requires the full IdentityIQ runtime; compiles against 8.4 identityiq.jar: " + e);
        }
    }
}
