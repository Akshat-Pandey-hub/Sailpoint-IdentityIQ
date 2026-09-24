package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * Native-source projection of a SailPoint {@code SyslogEvent} — an immutable operational/diagnostic log
 * record (distinct from AuditEvent). {@code username} is the raw actor name kept verbatim (never resolved
 * to an entity link). Pure data holder.
 */
public final class NativeSyslogEventRow {

    private String sourceId;
    private String quickKey;
    private String eventLevel;
    private String server;
    private String username;
    private String thread;
    private String lineNumber;
    private String message;
    private String stacktrace;
    private Instant created;

    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.SyslogEvent";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getQuickKey() { return quickKey; }
    public void setQuickKey(String v) { this.quickKey = v; }
    public String getEventLevel() { return eventLevel; }
    public void setEventLevel(String v) { this.eventLevel = v; }
    public String getServer() { return server; }
    public void setServer(String v) { this.server = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getThread() { return thread; }
    public void setThread(String v) { this.thread = v; }
    public String getLineNumber() { return lineNumber; }
    public void setLineNumber(String v) { this.lineNumber = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public String getStacktrace() { return stacktrace; }
    public void setStacktrace(String v) { this.stacktrace = v; }
    public Instant getCreated() { return created; }
    public void setCreated(Instant v) { this.created = v; }
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
