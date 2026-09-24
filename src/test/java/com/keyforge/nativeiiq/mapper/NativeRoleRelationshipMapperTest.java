package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;
import com.keyforge.nativeiiq.model.NativeRoleEntitlementRow;
import com.keyforge.nativeiiq.model.NativeRoleHierarchyRow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

/** Native model test self-skips when identityiq.jar's runtime dependencies are absent. */
class NativeRoleRelationshipMapperTest {
    @Test void mapsProfileConstraintPermissionAndTypedHierarchy() {
        try {
            Bundle role=new Bundle();role.setId("role-1");role.setName("Engineer");
            Application app=new Application();app.setId("app-1");app.setName("Directory");
            Profile profile=new Profile();profile.setApplication(app);profile.setConstraints(Collections.singletonList(Filter.eq("department","Engineering")));
            Permission permission=new Permission();permission.setTarget("cn=Engineering");permission.setRights(Arrays.asList("read","write"));
            profile.setPermissions(Collections.singletonList(permission));role.setProfiles(Collections.singletonList(profile));
            Bundle parent=new Bundle();parent.setId("role-parent");parent.setName("Parent");role.setInheritance(Collections.singletonList(parent));
            Bundle required=new Bundle();required.setId("role-required");role.setRequirements(Collections.singletonList(required));
            Bundle permit=new Bundle();permit.setId("role-permit");role.setPermits(Collections.singletonList(permit));
            NativeRoleRelationshipExtractionResult out=new NativeRoleRelationshipExtractionResult();
            NativeRoleRelationshipMapper.map(role,out,"IdentityIQ","run-1");
            assertEquals(2,out.getRoleEntitlements().size());
            NativeRoleEntitlementRow constraint=out.getRoleEntitlements().get(0);
            assertEquals("PROFILE_CONSTRAINT",constraint.getEntitlementType());assertEquals("department",constraint.getAttributeName());
            assertEquals("Engineering",constraint.getAttributeValue());assertEquals("app-1",constraint.getApplicationId());
            assertEquals("PROFILE_PERMISSION",out.getRoleEntitlements().get(1).getEntitlementType());
            assertEquals(3,out.getRoleHierarchy().size());
            assertEquals(Arrays.asList("INHERITANCE","REQUIREMENT","PERMIT"),Arrays.asList(
                    out.getRoleHierarchy().get(0).getRelationshipType(),out.getRoleHierarchy().get(1).getRelationshipType(),out.getRoleHierarchy().get(2).getRelationshipType()));
            assertNotNull(constraint.getSrcNaturalKey());assertEquals("run-1",constraint.getExtractionRunId());
        } catch (LinkageError e) {
            Assumptions.abort("Requires the full IdentityIQ runtime; signatures compile against 8.4 identityiq.jar: "+e);
        }
    }
}
