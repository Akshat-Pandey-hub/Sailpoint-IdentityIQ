package com.keyforge.nativeiiq.wire;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyforge.nativeiiq.model.NativeRoleEntitlementRow;
import com.keyforge.nativeiiq.model.NativeRoleHierarchyRow;
import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeRoleRelationshipWireTest {
    @Test void returnsOneJsonObjectAndPreservesNativeRelationshipTypes() throws Exception {
        NativeRoleRelationshipExtractionResult result=new NativeRoleRelationshipExtractionResult();
        NativeRoleEntitlementRow edge=new NativeRoleEntitlementRow();edge.setSourceBundleId("bundle-a");edge.setEntitlementType("PROFILE_CONSTRAINT");edge.setFilterValue(java.util.Arrays.asList("x",2));
        result.getRoleEntitlements().add(edge);
        NativeRoleHierarchyRow hierarchy=new NativeRoleHierarchyRow();hierarchy.setRelationshipType("REQUIREMENT");result.getRoleHierarchy().add(hierarchy);
        Map<String,Object> envelope=NativeRoleRelationshipWire.envelope(result,0,10,"run-1");
        String json=new ObjectMapper().writeValueAsString(envelope);
        assertTrue(json.startsWith("{"));assertFalse(json.startsWith("\"{"));assertTrue(json.contains("\"filterValue\":[\"x\",2]"));
        assertTrue(json.contains("\"relationshipType\":\"REQUIREMENT\""));assertEquals("run-1",envelope.get("extractionRunId"));
    }
}
