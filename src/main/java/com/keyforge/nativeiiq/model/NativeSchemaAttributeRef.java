package com.keyforge.nativeiiq.model;

/**
 * One native {@code sailpoint.object.AttributeDefinition} within an Application {@code Schema} — the
 * per-attribute semantics that explain what each account/group attribute means and how it links to
 * entitlements and other schemas. Pure data holder (no SailPoint dependency). Fields map to getters
 * verified in the 8.4 jar on {@code AttributeDefinition}/{@code BaseAttributeDefinition}:
 * {@code getName()}, {@code getType()}, {@code getDisplayName()}, {@code getDescription()},
 * {@code isEntitlement()}, {@code isManaged()}, {@code isMultiValued()}, {@code isGroup()},
 * {@code getSchemaObjectType()}, {@code getCorrelationKey()}, {@code isRequired()}, {@code isMinable()},
 * {@code isIndexed()}, {@code getSource()}, {@code getCompositeSourceApplication()},
 * {@code getCompositeSourceAttribute()}.
 *
 * <p>The pairing of {@code entitlement=true} with a {@code schemaObjectType} naming a group/role schema
 * is the account-attribute → entitlement/group edge; {@code correlationKey > 0} marks the attributes
 * used to correlate the account to an Identity.
 */
public final class NativeSchemaAttributeRef {

    private final String name;
    private final String type;
    private final String displayName;
    private final String description;
    private final Boolean entitlement;
    private final Boolean managed;
    private final Boolean multiValued;
    private final Boolean group;
    private final String schemaObjectType;
    private final Integer correlationKey;
    private final Boolean required;
    private final Boolean minable;
    private final Boolean indexed;
    private final String source;
    private final String compositeSourceApplication;
    private final String compositeSourceAttribute;

    public NativeSchemaAttributeRef(String name, String type, String displayName, String description,
                                    Boolean entitlement, Boolean managed, Boolean multiValued, Boolean group,
                                    String schemaObjectType, Integer correlationKey, Boolean required,
                                    Boolean minable, Boolean indexed, String source,
                                    String compositeSourceApplication, String compositeSourceAttribute) {
        this.name = name;
        this.type = type;
        this.displayName = displayName;
        this.description = description;
        this.entitlement = entitlement;
        this.managed = managed;
        this.multiValued = multiValued;
        this.group = group;
        this.schemaObjectType = schemaObjectType;
        this.correlationKey = correlationKey;
        this.required = required;
        this.minable = minable;
        this.indexed = indexed;
        this.source = source;
        this.compositeSourceApplication = compositeSourceApplication;
        this.compositeSourceAttribute = compositeSourceAttribute;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Boolean getEntitlement() { return entitlement; }
    public Boolean getManaged() { return managed; }
    public Boolean getMultiValued() { return multiValued; }
    public Boolean getGroup() { return group; }
    public String getSchemaObjectType() { return schemaObjectType; }
    public Integer getCorrelationKey() { return correlationKey; }
    public Boolean getRequired() { return required; }
    public Boolean getMinable() { return minable; }
    public Boolean getIndexed() { return indexed; }
    public String getSource() { return source; }
    public String getCompositeSourceApplication() { return compositeSourceApplication; }
    public String getCompositeSourceAttribute() { return compositeSourceAttribute; }
}
