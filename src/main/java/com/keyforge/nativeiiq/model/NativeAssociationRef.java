package com.keyforge.nativeiiq.model;

/**
 * A plain reference to one native {@code sailpoint.object.TargetAssociation} of a ManagedAttribute
 * (group/association linkage). Pure data holder — no SailPoint dependency. Fields map to the 8.4
 * getters: {@code getTargetName()}, {@code getTargetType()}, {@code getOwnerType()}, {@code getOwnerId()},
 * {@code getApplicationName()}, {@code getObjectId()}, {@code getRights()}, {@code getEffective()},
 * {@code getLastAggregation()}, {@code getHierarchy()}, {@code getEffectiveTargetName()},
 * {@code getUniqueTargetName()}, and the flags {@code isInherited/isFlattened/isPermission/isAccount/
 * isAttribute/isAllowPermission/isDenyPermission/isUnstructured/isIiqElevatedAccess}. Source values are
 * preserved exactly; a native null stays null. The nested {@code getTarget()} -> {@code Target} object is a
 * separate SailPointObject (its own identity + AccessMapping graph); its id is already preserved here as
 * {@code objectId}, so the full Target is intentionally not flattened into this ref.
 */
public final class NativeAssociationRef {

    private final String targetName;
    private final String targetType;
    private final String ownerType;
    private final String ownerId;
    private final String applicationName;
    private final String objectId;
    private final String rights;
    private final int effective;
    private final String lastAggregation;
    private final String hierarchy;
    private final String effectiveTargetName;
    private final String uniqueTargetName;
    private final boolean inherited;
    private final boolean flattened;
    private final boolean permission;
    private final boolean account;
    private final boolean attribute;
    private final boolean allowPermission;
    private final boolean denyPermission;
    private final boolean unstructured;
    private final boolean iiqElevatedAccess;

    public NativeAssociationRef(String targetName, String targetType, String ownerType, String ownerId,
                                String applicationName, String objectId, String rights, int effective,
                                String lastAggregation, String hierarchy, String effectiveTargetName,
                                String uniqueTargetName, boolean inherited, boolean flattened, boolean permission,
                                boolean account, boolean attribute, boolean allowPermission, boolean denyPermission,
                                boolean unstructured, boolean iiqElevatedAccess) {
        this.targetName = targetName;
        this.targetType = targetType;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.applicationName = applicationName;
        this.objectId = objectId;
        this.rights = rights;
        this.effective = effective;
        this.lastAggregation = lastAggregation;
        this.hierarchy = hierarchy;
        this.effectiveTargetName = effectiveTargetName;
        this.uniqueTargetName = uniqueTargetName;
        this.inherited = inherited;
        this.flattened = flattened;
        this.permission = permission;
        this.account = account;
        this.attribute = attribute;
        this.allowPermission = allowPermission;
        this.denyPermission = denyPermission;
        this.unstructured = unstructured;
        this.iiqElevatedAccess = iiqElevatedAccess;
    }

    public String getTargetName() { return targetName; }
    public String getTargetType() { return targetType; }
    public String getOwnerType() { return ownerType; }
    public String getOwnerId() { return ownerId; }
    public String getApplicationName() { return applicationName; }
    public String getObjectId() { return objectId; }
    public String getRights() { return rights; }
    public int getEffective() { return effective; }
    public String getLastAggregation() { return lastAggregation; }
    public String getHierarchy() { return hierarchy; }
    public String getEffectiveTargetName() { return effectiveTargetName; }
    public String getUniqueTargetName() { return uniqueTargetName; }
    public boolean isInherited() { return inherited; }
    public boolean isFlattened() { return flattened; }
    public boolean isPermission() { return permission; }
    public boolean isAccount() { return account; }
    public boolean isAttribute() { return attribute; }
    public boolean isAllowPermission() { return allowPermission; }
    public boolean isDenyPermission() { return denyPermission; }
    public boolean isUnstructured() { return unstructured; }
    public boolean isIiqElevatedAccess() { return iiqElevatedAccess; }
}
