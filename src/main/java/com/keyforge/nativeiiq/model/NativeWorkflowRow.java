package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native Workflow definition reduced to JSON-safe source values. */
public final class NativeWorkflowRow {
    private String sourceId, name, type, handler, description, taskType, srcSystem, extractionRunId;
    private Instant created, modified, extractedAt;
    private final Map<String,Object> definition = new LinkedHashMap<String,Object>();
    public String getSourceId(){return sourceId;} public void setSourceId(String v){sourceId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getType(){return type;} public void setType(String v){type=v;}
    public String getHandler(){return handler;} public void setHandler(String v){handler=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getTaskType(){return taskType;} public void setTaskType(String v){taskType=v;}
    public Instant getCreated(){return created;} public void setCreated(Instant v){created=v;}
    public Instant getModified(){return modified;} public void setModified(Instant v){modified=v;}
    public Instant getExtractedAt(){return extractedAt;} public void setExtractedAt(Instant v){extractedAt=v;}
    public String getSrcSystem(){return srcSystem;} public void setSrcSystem(String v){srcSystem=v;}
    public String getExtractionRunId(){return extractionRunId;} public void setExtractionRunId(String v){extractionRunId=v;}
    public Map<String,Object> getDefinition(){return definition;}
}
