package com.keyforge.nativeload;
import java.time.Instant;import java.util.List;
/** Plain transport record; no SailPoint runtime object escapes the plugin. */
final class NativeRoleRelationshipRecord{
 String sourceBundleId,roleId,roleName,entitlementType,applicationId,application,attributeName,attributeValue,filterOperation,filterExpression,constraintPath,permissionTarget,permissionRights,permissionAnnotation,srcNaturalKey,sourceSystem,srcInterface,srcObjectType,extractionRunId;
 Object filterValue;
 int profileOrdinal; List<String> permissionRightsList; Instant extractedAt;
}
