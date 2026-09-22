package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * Native-source projection of a SailPoint {@code GroupDefinition}. In IdentityIQ 8.4 both Populations
 * and Groups are {@code GroupDefinition} objects; the two are distinguished by the presence of a
 * {@code GroupFactory}: a definition tied to a factory is a <b>GROUP</b> (factory-generated per
 * identity-attribute value), a definition with no factory is a <b>POPULATION</b> (a standalone
 * filter-defined set). Pure data holder (no SailPoint dependency).
 *
 * <p>Stage 7 scope is the definition ENTITY only — the intrinsic {@code filter} criteria is stored as
 * text because it is part of the definition itself, but no membership relationship is materialized.
 */
public final class NativeGroupDefinitionRow {

    // --- identity / core ---
    private String sourceId;
    private String name;
    /** Derived, source-backed: {@code "GROUP"} when a factory is present, else {@code "POPULATION"}. */
    private String type;

    // --- raw distinction evidence (factory reference; null for populations) ---
    private String factoryId;
    private String factoryName;

    // --- intrinsic definition criteria (filter expression text) ---
    private String filterExpression;

    // --- native flags ---
    private Boolean isPrivate;
    private Boolean indexed;
    private Boolean nullGroup;
    private Boolean nameUnique;

    // --- owner (native reference) ---
    private String ownerId;
    private String ownerName;

    // --- source timestamps ---
    private Instant lastRefresh;
    private Instant created;
    private Instant modified;

    // --- lineage envelope ---
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.GroupDefinition";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getFactoryId() { return factoryId; }
    public void setFactoryId(String v) { this.factoryId = v; }

    public String getFactoryName() { return factoryName; }
    public void setFactoryName(String v) { this.factoryName = v; }

    public String getFilterExpression() { return filterExpression; }
    public void setFilterExpression(String v) { this.filterExpression = v; }

    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean v) { this.isPrivate = v; }

    public Boolean getIndexed() { return indexed; }
    public void setIndexed(Boolean v) { this.indexed = v; }

    public Boolean getNullGroup() { return nullGroup; }
    public void setNullGroup(Boolean v) { this.nullGroup = v; }

    public Boolean getNameUnique() { return nameUnique; }
    public void setNameUnique(Boolean v) { this.nameUnique = v; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }

    public Instant getLastRefresh() { return lastRefresh; }
    public void setLastRefresh(Instant v) { this.lastRefresh = v; }

    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }

    public Instant getModified() { return modified; }
    public void setModified(Instant v) { this.modified = v; }

    public String getSrcSystem() { return srcSystem; }
    public void setSrcSystem(String v) { this.srcSystem = v; }

    public String getSrcInterface() { return srcInterface; }

    public String getSrcObjectType() { return srcObjectType; }
    public void setSrcObjectType(String v) { this.srcObjectType = v; }

    public String getExtractionRunId() { return extractionRunId; }
    public void setExtractionRunId(String v) { this.extractionRunId = v; }

    public Instant getExtractedAt() { return extractedAt; }
    public void setExtractedAt(Instant v) { this.extractedAt = v; }
}
