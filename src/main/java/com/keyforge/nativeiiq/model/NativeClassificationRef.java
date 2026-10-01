package com.keyforge.nativeiiq.model;

/**
 * A plain reference to one native {@code sailpoint.object.ObjectClassification} of a ManagedAttribute.
 * Pure data holder — no SailPoint dependency. Fields map to the 8.4 getters {@code getSource()},
 * {@code getOwnerType()}, {@code getOwnerId()}, {@code isEffective()} and, via {@code getClassification()},
 * the {@code Classification}'s {@code getName()}, {@code getDisplayableName()}, {@code getType()},
 * {@code getOrigin()}. Values are preserved exactly as SailPoint returns them; a native null stays null.
 */
public final class NativeClassificationRef {

    private final String classificationName;
    private final String classificationDisplayName;
    private final String classificationType;
    private final String classificationOrigin;
    private final String source;
    private final String ownerType;
    private final String ownerId;
    private final Boolean effective;

    public NativeClassificationRef(String classificationName, String classificationDisplayName,
                                   String classificationType, String classificationOrigin, String source,
                                   String ownerType, String ownerId, Boolean effective) {
        this.classificationName = classificationName;
        this.classificationDisplayName = classificationDisplayName;
        this.classificationType = classificationType;
        this.classificationOrigin = classificationOrigin;
        this.source = source;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.effective = effective;
    }

    public String getClassificationName() { return classificationName; }
    public String getClassificationDisplayName() { return classificationDisplayName; }
    public String getClassificationType() { return classificationType; }
    public String getClassificationOrigin() { return classificationOrigin; }
    public String getSource() { return source; }
    public String getOwnerType() { return ownerType; }
    public String getOwnerId() { return ownerId; }
    public Boolean getEffective() { return effective; }
}
