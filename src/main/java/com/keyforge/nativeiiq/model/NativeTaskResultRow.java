package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code TaskResult} (a task/aggregation run record), produced
 * by the native Java-API layer. This is the execution/status/history evidence the delimited-application
 * prioritization use case (TF-DEL-001) needs — task {@code type}, completion status, launch/complete
 * timestamps, run length, and the statistics attribute map. Pure data holder (no SailPoint dependency).
 *
 * <p>Distinct from the REST/SCIM {@code task_result} dataset: same business object, separate native
 * model and {@code native_iiq_java_api} lineage so the two source planes stay distinguishable.
 */
public final class NativeTaskResultRow {

    private String sourceId;
    private String name;
    private String type;
    private String completionStatus;
    private String definitionName;
    private String launcher;
    private String host;
    private String targetName;
    private String targetClass;
    private String targetId;
    private String schedule;
    private String progress;

    private Integer percentComplete;
    private Integer runLength;
    private Integer pendingSignoffs;

    private Boolean partitioned;
    private Boolean terminateRequested;
    private Boolean complete;

    private Instant launched;
    private Instant completed;
    private Instant expiration;
    private Instant verified;

    private final List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    private Instant created;
    private Instant modified;

    // lineage envelope
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.TaskResult";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public String getCompletionStatus() { return completionStatus; }
    public void setCompletionStatus(String v) { this.completionStatus = v; }

    public String getDefinitionName() { return definitionName; }
    public void setDefinitionName(String v) { this.definitionName = v; }

    public String getLauncher() { return launcher; }
    public void setLauncher(String v) { this.launcher = v; }

    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String v) { this.targetName = v; }

    public String getTargetClass() { return targetClass; }
    public void setTargetClass(String v) { this.targetClass = v; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String v) { this.schedule = v; }

    public String getProgress() { return progress; }
    public void setProgress(String v) { this.progress = v; }

    public Integer getPercentComplete() { return percentComplete; }
    public void setPercentComplete(Integer v) { this.percentComplete = v; }

    public Integer getRunLength() { return runLength; }
    public void setRunLength(Integer v) { this.runLength = v; }

    public Integer getPendingSignoffs() { return pendingSignoffs; }
    public void setPendingSignoffs(Integer v) { this.pendingSignoffs = v; }

    public Boolean getPartitioned() { return partitioned; }
    public void setPartitioned(Boolean v) { this.partitioned = v; }

    public Boolean getTerminateRequested() { return terminateRequested; }
    public void setTerminateRequested(Boolean v) { this.terminateRequested = v; }

    public Boolean getComplete() { return complete; }
    public void setComplete(Boolean v) { this.complete = v; }

    public Instant getLaunched() { return launched; }
    public void setLaunched(Instant v) { this.launched = v; }

    public Instant getCompleted() { return completed; }
    public void setCompleted(Instant v) { this.completed = v; }

    public Instant getExpiration() { return expiration; }
    public void setExpiration(Instant v) { this.expiration = v; }

    public Instant getVerified() { return verified; }
    public void setVerified(Instant v) { this.verified = v; }

    public List<Map<String, Object>> getMessages() { return messages; }
    public Map<String, Object> getAttributes() { return attributes; }

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
