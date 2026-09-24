package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native-source projection of a SailPoint {@code AuditEvent} — an immutable historical audit record.
 * The {@code source}/{@code target}/{@code application} fields are the raw audit strings (names), kept
 * verbatim; they are NOT turned into authoritative entity links. Pure data holder.
 */
public final class NativeAuditEventRow {

    private String sourceId;
    private String action;
    private String auditSource;
    private String target;
    private String application;
    private String accountName;
    private String instance;
    private String attributeName;
    private String attributeValue;
    private String interfaceName;
    private String serverHost;
    private String clientHost;
    private String trackingId;
    private String string1;
    private String string2;
    private String string3;
    private String string4;
    private final Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    private Instant created;

    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.AuditEvent";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getAuditSource() { return auditSource; }
    public void setAuditSource(String v) { this.auditSource = v; }
    public String getTarget() { return target; }
    public void setTarget(String v) { this.target = v; }
    public String getApplication() { return application; }
    public void setApplication(String v) { this.application = v; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String v) { this.accountName = v; }
    public String getInstance() { return instance; }
    public void setInstance(String v) { this.instance = v; }
    public String getAttributeName() { return attributeName; }
    public void setAttributeName(String v) { this.attributeName = v; }
    public String getAttributeValue() { return attributeValue; }
    public void setAttributeValue(String v) { this.attributeValue = v; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String v) { this.interfaceName = v; }
    public String getServerHost() { return serverHost; }
    public void setServerHost(String v) { this.serverHost = v; }
    public String getClientHost() { return clientHost; }
    public void setClientHost(String v) { this.clientHost = v; }
    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String v) { this.trackingId = v; }
    public String getString1() { return string1; }
    public void setString1(String v) { this.string1 = v; }
    public String getString2() { return string2; }
    public void setString2(String v) { this.string2 = v; }
    public String getString3() { return string3; }
    public void setString3(String v) { this.string3 = v; }
    public String getString4() { return string4; }
    public void setString4(String v) { this.string4 = v; }
    public Map<String, Object> getAttributes() { return attributes; }
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
