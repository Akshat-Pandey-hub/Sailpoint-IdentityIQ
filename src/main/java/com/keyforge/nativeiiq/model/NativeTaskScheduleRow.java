package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code TaskSchedule} (the cron schedule that launches a task
 * definition — e.g. a delimited-application aggregation). This is the "expected schedule" evidence the
 * delimited-application prioritization use case (TF-DEL-001) needs: cron expressions, next/last run
 * times, and the linked task-definition name. Pure data holder (no SailPoint dependency).
 */
public final class NativeTaskScheduleRow {

    private String sourceId;
    private String name;
    private String description;
    private String definitionName;
    private String state;
    private String newState;
    private String launcher;
    private String host;
    private String lastLaunchError;

    private Boolean deleteOnFinish;

    private Instant lastExecution;
    private Instant nextExecution;
    private Instant nextActualExecution;
    private Instant resumeDate;

    private final List<String> cronExpressions = new ArrayList<String>();
    private final Map<String, Object> arguments = new LinkedHashMap<String, Object>();

    private Instant created;
    private Instant modified;

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.TaskSchedule";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getDefinitionName() { return definitionName; }
    public void setDefinitionName(String v) { this.definitionName = v; }

    public String getState() { return state; }
    public void setState(String v) { this.state = v; }

    public String getNewState() { return newState; }
    public void setNewState(String v) { this.newState = v; }

    public String getLauncher() { return launcher; }
    public void setLauncher(String v) { this.launcher = v; }

    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }

    public String getLastLaunchError() { return lastLaunchError; }
    public void setLastLaunchError(String v) { this.lastLaunchError = v; }

    public Boolean getDeleteOnFinish() { return deleteOnFinish; }
    public void setDeleteOnFinish(Boolean v) { this.deleteOnFinish = v; }

    public Instant getLastExecution() { return lastExecution; }
    public void setLastExecution(Instant v) { this.lastExecution = v; }

    public Instant getNextExecution() { return nextExecution; }
    public void setNextExecution(Instant v) { this.nextExecution = v; }

    public Instant getNextActualExecution() { return nextActualExecution; }
    public void setNextActualExecution(Instant v) { this.nextActualExecution = v; }

    public Instant getResumeDate() { return resumeDate; }
    public void setResumeDate(Instant v) { this.resumeDate = v; }

    public List<String> getCronExpressions() { return cronExpressions; }
    public Map<String, Object> getArguments() { return arguments; }

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
