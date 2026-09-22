package com.keyforge.nativeiiq.model;

/**
 * A compact reference to one native {@code sailpoint.object.Schema} of an Application. Pure data holder
 * — no SailPoint dependency. Fields map to the 8.4 getters {@code getObjectType()},
 * {@code getNativeObjectType()}, {@code getIdentityAttribute()}, {@code getDisplayAttribute()},
 * {@code getInstanceAttribute()} plus the attribute-definition count.
 */
public final class NativeSchemaRef {

    private final String objectType;
    private final String nativeObjectType;
    private final String identityAttribute;
    private final String displayAttribute;
    private final String instanceAttribute;
    private final Integer attributeCount;

    public NativeSchemaRef(String objectType, String nativeObjectType, String identityAttribute,
                           String displayAttribute, String instanceAttribute, Integer attributeCount) {
        this.objectType = objectType;
        this.nativeObjectType = nativeObjectType;
        this.identityAttribute = identityAttribute;
        this.displayAttribute = displayAttribute;
        this.instanceAttribute = instanceAttribute;
        this.attributeCount = attributeCount;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getNativeObjectType() {
        return nativeObjectType;
    }

    public String getIdentityAttribute() {
        return identityAttribute;
    }

    public String getDisplayAttribute() {
        return displayAttribute;
    }

    public String getInstanceAttribute() {
        return instanceAttribute;
    }

    public Integer getAttributeCount() {
        return attributeCount;
    }
}
