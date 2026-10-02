package com.keyforge.nativeiiq.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One native {@code sailpoint.object.Schema} of an Application. Pure data holder — no SailPoint
 * dependency. The core identity fields map to the 8.4 getters {@code getObjectType()},
 * {@code getNativeObjectType()}, {@code getIdentityAttribute()}, {@code getDisplayAttribute()},
 * {@code getInstanceAttribute()} plus the attribute-definition count. The enrichment fields
 * (group/hierarchy/features/entitlement-attribute names and the per-attribute {@link
 * NativeSchemaAttributeRef} list) carry the schema semantics — which attributes are entitlements, which
 * reference other schemas (groups/roles), and which are correlation keys — that a bare count loses.
 */
public final class NativeSchemaRef {

    private final String objectType;
    private final String nativeObjectType;
    private final String identityAttribute;
    private final String displayAttribute;
    private final String instanceAttribute;
    private final Integer attributeCount;

    // --- enrichment: schema-level metadata (set after construction; null stays null) ---
    private String featuresString;
    private String groupAttribute;
    private String hierarchyAttribute;
    private String descriptionAttribute;
    private String aggregationType;
    private String associationSchemaName;
    private Boolean includePermissions;
    private Boolean indexPermissions;
    private Boolean groupAggregation;
    private Boolean childHierarchy;
    private final List<String> entitlementAttributeNames = new ArrayList<String>();
    private final List<NativeSchemaAttributeRef> attributes = new ArrayList<NativeSchemaAttributeRef>();

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

    public String getFeaturesString() { return featuresString; }
    public void setFeaturesString(String v) { this.featuresString = v; }

    public String getGroupAttribute() { return groupAttribute; }
    public void setGroupAttribute(String v) { this.groupAttribute = v; }

    public String getHierarchyAttribute() { return hierarchyAttribute; }
    public void setHierarchyAttribute(String v) { this.hierarchyAttribute = v; }

    public String getDescriptionAttribute() { return descriptionAttribute; }
    public void setDescriptionAttribute(String v) { this.descriptionAttribute = v; }

    public String getAggregationType() { return aggregationType; }
    public void setAggregationType(String v) { this.aggregationType = v; }

    public String getAssociationSchemaName() { return associationSchemaName; }
    public void setAssociationSchemaName(String v) { this.associationSchemaName = v; }

    public Boolean getIncludePermissions() { return includePermissions; }
    public void setIncludePermissions(Boolean v) { this.includePermissions = v; }

    public Boolean getIndexPermissions() { return indexPermissions; }
    public void setIndexPermissions(Boolean v) { this.indexPermissions = v; }

    public Boolean getGroupAggregation() { return groupAggregation; }
    public void setGroupAggregation(Boolean v) { this.groupAggregation = v; }

    public Boolean getChildHierarchy() { return childHierarchy; }
    public void setChildHierarchy(Boolean v) { this.childHierarchy = v; }

    public List<String> getEntitlementAttributeNames() { return entitlementAttributeNames; }
    public List<NativeSchemaAttributeRef> getAttributes() { return attributes; }
}
