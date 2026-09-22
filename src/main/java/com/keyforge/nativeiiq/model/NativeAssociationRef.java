package com.keyforge.nativeiiq.model;

/**
 * A plain reference to one native {@code sailpoint.object.TargetAssociation} of a ManagedAttribute
 * (group/association linkage). Pure data holder — no SailPoint dependency. Fields map to the 8.4
 * getters {@code getTargetName()}, {@code getTargetType()}, {@code getOwnerType()},
 * {@code getApplicationName()}, {@code getObjectId()}.
 */
public final class NativeAssociationRef {

    private final String targetName;
    private final String targetType;
    private final String ownerType;
    private final String applicationName;
    private final String objectId;

    public NativeAssociationRef(String targetName, String targetType, String ownerType,
                                String applicationName, String objectId) {
        this.targetName = targetName;
        this.targetType = targetType;
        this.ownerType = ownerType;
        this.applicationName = applicationName;
        this.objectId = objectId;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getObjectId() {
        return objectId;
    }
}
