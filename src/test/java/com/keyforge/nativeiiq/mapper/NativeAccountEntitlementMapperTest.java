package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeAccountEntitlementRow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import sailpoint.object.Permission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the native SailPoint-side wiring of the Account-Entitlement permission expansion against the real
 * 8.4 {@code identityiq.jar}: {@code mapPermissionsInto} reads getTarget/getRights/getRightsList/getAnnotation/
 * getAggregationSource/getAttributes verbatim onto a PERMISSION (or TARGET_PERMISSION) edge, preserving the
 * account context and leaving attribute_* unset. Self-skips when the full IIQ runtime is absent (compiles only
 * under the {@code native} profile). The attribute-edge path is covered end-to-end by the nativeload
 * parser/REST tests which run in the default suite.
 */
class NativeAccountEntitlementMapperTest {

    @Test
    void mapsPermissionEdgeFieldsAndKeepsAccountContext() {
        try {
            Permission p = new Permission(Arrays.asList("read", "write"), "/finance/reports");
            p.setAnnotation("Finance reports");
            p.setAggregationSource("aggregation-task");
            p.setAttribute("scope", "dept");

            List<NativeAccountEntitlementRow> out = new ArrayList<NativeAccountEntitlementRow>();
            int n = NativeAccountEntitlementMapper.mapPermissionsInto(out,
                    java.util.Collections.singletonList(p), NativeAccountEntitlementMapper.TYPE_PERMISSION,
                    "sailpoint.object.Link.permissions", "link-1", "id-1", "Alexander Roberts",
                    "app-1", "UnixApp", "aroberts", null, "IdentityIQ", "run-1");

            assertEquals(1, n);
            NativeAccountEntitlementRow row = out.get(0);
            assertEquals("PERMISSION", row.getType());
            assertEquals("sailpoint.object.Link.permissions", row.getSrcObjectType());
            // account context preserved
            assertEquals("link-1", row.getLinkId());
            assertEquals("id-1", row.getIdentityId());
            assertEquals("Alexander Roberts", row.getIdentityName());
            assertEquals("app-1", row.getApplicationId());
            assertEquals("UnixApp", row.getApplicationName());
            assertEquals("aroberts", row.getNativeIdentity());
            // permission payload read verbatim
            assertEquals("/finance/reports", row.getPermissionTarget());
            assertNotNull(row.getPermissionRights(), "getRights() comma-joined");
            assertTrue(row.getPermissionRights().contains("read"));
            assertEquals(Arrays.asList("read", "write"), row.getPermissionRightsList());
            assertEquals("Finance reports", row.getPermissionAnnotation());
            assertEquals("aggregation-task", row.getPermissionAggregationSource());
            assertEquals("dept", row.getPermissionAttributes().get("scope"));
            // a permission edge carries no attribute fields
            assertNull(row.getAttributeName());
            assertNull(row.getAttributeValue());
        } catch (LinkageError e) {
            Assumptions.abort("Requires the full IdentityIQ runtime; compiles against 8.4 identityiq.jar: " + e);
        }
    }

    @Test
    void targetPermissionEdgeUsesItsOwnType() {
        try {
            Permission p = new Permission(Arrays.asList("read"), "/share/hr");

            List<NativeAccountEntitlementRow> out = new ArrayList<NativeAccountEntitlementRow>();
            NativeAccountEntitlementMapper.mapPermissionsInto(out,
                    java.util.Collections.singletonList(p),
                    NativeAccountEntitlementMapper.TYPE_TARGET_PERMISSION,
                    "sailpoint.object.Link.targetPermissions", "link-1", "id-1", "Alexander Evans",
                    "app-1", "UnixApp", "aevans", null, "IdentityIQ", "run-1");

            assertEquals(1, out.size());
            assertEquals("TARGET_PERMISSION", out.get(0).getType());
            assertEquals("/share/hr", out.get(0).getPermissionTarget());
        } catch (LinkageError e) {
            Assumptions.abort("Requires the full IdentityIQ runtime; compiles against 8.4 identityiq.jar: " + e);
        }
    }

    @Test
    void nullOrEmptyPermissionListProducesNoEdges() {
        List<NativeAccountEntitlementRow> out = new ArrayList<NativeAccountEntitlementRow>();
        int n1 = NativeAccountEntitlementMapper.mapPermissionsInto(out, null,
                NativeAccountEntitlementMapper.TYPE_PERMISSION, "sailpoint.object.Link.permissions",
                "link-1", null, null, null, null, null, null, "IdentityIQ", "run-1");
        int n2 = NativeAccountEntitlementMapper.mapPermissionsInto(out, java.util.Collections.emptyList(),
                NativeAccountEntitlementMapper.TYPE_PERMISSION, "sailpoint.object.Link.permissions",
                "link-1", null, null, null, null, null, null, "IdentityIQ", "run-1");
        assertEquals(0, n1);
        assertEquals(0, n2);
        assertTrue(out.isEmpty());
    }
}
