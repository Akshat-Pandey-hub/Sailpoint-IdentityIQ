package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeRoleEntitlementRow;
import com.keyforge.nativeiiq.model.NativeRoleHierarchyRow;
import com.keyforge.nativeiiq.model.NativeRoleRelationshipExtractionResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-object envelope for Bundle profile and hierarchy edges. */
public final class NativeRoleRelationshipWire {
    private NativeRoleRelationshipWire() { }

    public static Map<String, Object> envelope(NativeRoleRelationshipExtractionResult result,
                                                int start, int limit, String runId) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("entity", "BundleRelationships");
        root.put("start", start);
        root.put("limit", limit);
        root.put("sourceCount", result.getSourceCount());
        root.put("returnedBundles", result.getReturnedBundles());
        root.put("extractionRunId", runId);
        List<Map<String, Object>> entitlements = new ArrayList<Map<String, Object>>();
        for (NativeRoleEntitlementRow row : result.getRoleEntitlements()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("sourceBundleId", row.getSourceBundleId());
            item.put("roleId", row.getRoleId()); item.put("roleName", row.getRoleName());
            item.put("profileOrdinal", row.getProfileOrdinal()); item.put("constraintPath", row.getConstraintPath());
            item.put("filterExpression", row.getFilterExpression()); item.put("filterValue", row.getFilterValue());
            item.put("entitlementType", row.getEntitlementType()); item.put("applicationId", row.getApplicationId());
            item.put("application", row.getApplication()); item.put("attributeName", row.getAttributeName());
            item.put("attributeValue", row.getAttributeValue()); item.put("filterOperation", row.getFilterOperation());
            item.put("permissionTarget", row.getPermissionTarget()); item.put("permissionRights", row.getPermissionRights());
            item.put("permissionRightsList", row.getPermissionRightsList()); item.put("permissionAnnotation", row.getPermissionAnnotation());
            item.put("sourceSystem", row.getSourceSystem()); item.put("srcInterface", row.getSrcInterface());
            item.put("srcObjectType", row.getSrcObjectType()); item.put("srcNaturalKey", row.getSrcNaturalKey());
            item.put("extractionRunId", runId); item.put("extractedAt", row.getExtractedAt());
            entitlements.add(item);
        }
        root.put("roleEntitlements", entitlements);
        List<Map<String, Object>> hierarchy = new ArrayList<Map<String, Object>>();
        for (NativeRoleHierarchyRow row : result.getRoleHierarchy()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("sourceRoleId", row.getSourceRoleId()); item.put("sourceRoleName", row.getSourceRoleName());
            item.put("relatedRoleId", row.getRelatedRoleId()); item.put("relatedRoleName", row.getRelatedRoleName());
            item.put("relationshipType", row.getRelationshipType()); item.put("sourceSystem", row.getSourceSystem());
            item.put("srcNaturalKey", row.getSrcNaturalKey()); item.put("extractionRunId", runId);
            item.put("extractedAt", row.getExtractedAt()); hierarchy.add(item);
        }
        root.put("roleHierarchy", hierarchy);
        return root;
    }
}
