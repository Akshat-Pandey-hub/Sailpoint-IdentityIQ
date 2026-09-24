package com.keyforge.nativeiiq.model;

/** A source-backed Bundle-to-Bundle relationship; relationshipType is INHERITANCE, REQUIREMENT, or PERMIT. */
public final class NativeRoleHierarchyRow {
    private String sourceRoleId, sourceRoleName, relatedRoleId, relatedRoleName, relationshipType;
    private String sourceSystem, extractionRunId, srcNaturalKey, extractedAt;
    public String getSourceRoleId(){return sourceRoleId;} public void setSourceRoleId(String v){sourceRoleId=v;}
    public String getSourceRoleName(){return sourceRoleName;} public void setSourceRoleName(String v){sourceRoleName=v;}
    public String getRelatedRoleId(){return relatedRoleId;} public void setRelatedRoleId(String v){relatedRoleId=v;}
    public String getRelatedRoleName(){return relatedRoleName;} public void setRelatedRoleName(String v){relatedRoleName=v;}
    public String getRelationshipType(){return relationshipType;} public void setRelationshipType(String v){relationshipType=v;}
    public String getSourceSystem(){return sourceSystem;} public void setSourceSystem(String v){sourceSystem=v;}
    public String getExtractionRunId(){return extractionRunId;} public void setExtractionRunId(String v){extractionRunId=v;}
    public String getSrcNaturalKey(){return srcNaturalKey;} public void setSrcNaturalKey(String v){srcNaturalKey=v;}
    public String getExtractedAt(){return extractedAt;} public void setExtractedAt(String v){extractedAt=v;}
}
