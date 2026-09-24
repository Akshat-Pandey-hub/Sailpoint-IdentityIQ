package com.keyforge.nativeiiq.model;

/**
 * Native-source projection of one role&nbsp;&rarr;&nbsp;entitlement edge, derived from a {@code Bundle}'s
 * {@code getProfiles()}: each Profile's application + filter-constraint leaves (attribute==value) become
 * ATTRIBUTE edges, and each Permission becomes a PERMISSION edge. The direct ManagedAttribute id is NOT
 * exposed by Profile, so the relationship key is application + attribute + value; the loader resolves the
 * entitlement id from existing native entitlement data when possible. Pure data holder.
 */
public final class NativeRoleEntitlementRow {

    private String sourceBundleId;
    private int profileOrdinal;
    private String constraintPath;
    private String filterExpression;
    private Object filterValue;
    private String roleId;
    private String roleName;
    private String entitlementType;   // ATTRIBUTE | PERMISSION
    private String application;
    private String attributeName;     // Filter leaf property (ATTRIBUTE)
    private String attributeValue;    // Filter leaf value (ATTRIBUTE)
    private String filterOperation;   // Filter leaf operation
    private String permissionTarget;  // Permission target (PERMISSION)
    private String permissionRights;  // Permission rights (PERMISSION)
    private java.util.List<String> permissionRightsList = new java.util.ArrayList<String>();
    private String permissionAnnotation;
    private String applicationId;
    private String sourceSystem;
    private String extractionRunId;
    private String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.Bundle";
    private String srcNaturalKey;
    private String extractedAt;

    public String getSourceBundleId() { return sourceBundleId; }
    public void setSourceBundleId(String v) { this.sourceBundleId = v; }
    public int getProfileOrdinal() { return profileOrdinal; }
    public void setProfileOrdinal(int v) { this.profileOrdinal = v; }
    public String getConstraintPath() { return constraintPath; }
    public void setConstraintPath(String v) { this.constraintPath = v; }
    public String getFilterExpression() { return filterExpression; }
    public void setFilterExpression(String v) { this.filterExpression = v; }
    public Object getFilterValue() { return filterValue; }
    public void setFilterValue(Object v) { this.filterValue = v; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String v) { this.roleId = v; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String v) { this.roleName = v; }
    public String getEntitlementType() { return entitlementType; }
    public void setEntitlementType(String v) { this.entitlementType = v; }
    public String getApplication() { return application; }
    public void setApplication(String v) { this.application = v; }
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String v) { this.attributeName = v; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String v) { this.attributeValue = v; }
    public String getFilterOperation() { return filterOperation; }
    public void setFilterOperation(String v) { this.filterOperation = v; }
    public String getPermissionTarget() { return permissionTarget; }
    public void setPermissionTarget(String v) { this.permissionTarget = v; }
    public String getPermissionRights() { return permissionRights; }
    public void setPermissionRights(String v) { this.permissionRights = v; }
    public java.util.List<String> getPermissionRightsList() { return permissionRightsList; }
    public void setPermissionRightsList(java.util.List<String> v) { this.permissionRightsList = v; }
    public String getPermissionAnnotation() { return permissionAnnotation; }
    public void setPermissionAnnotation(String v) { this.permissionAnnotation = v; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String v) { this.sourceSystem = v; }
    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }
    public String getSrcInterface() { return srcInterface; }
    public String getSrcObjectType() { return srcObjectType; }
    public String getSrcNaturalKey() { return srcNaturalKey; }
    public void setSrcNaturalKey(String v) { this.srcNaturalKey = v; }
    public String getExtractedAt() { return extractedAt; }
    public void setExtractedAt(String v) { this.extractedAt = v; }
}
