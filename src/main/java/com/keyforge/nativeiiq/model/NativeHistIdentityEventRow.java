package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * Native-source projection of a SailPoint {@code accesshistory.HistoricalIdentityEvent} — one immutable
 * Access-History event (property change / account / governance action) recorded against a HistoricalIdentity.
 * Field-complete over the class and its {@code HistoricalEvent} + {@code QueryableAccessHistory} parents;
 * enum values are kept as their source {@code name()}, the raw detail payload is kept verbatim, NULLs
 * preserved. Pure data holder; references are explicit source ids only, never name-inferred links.
 */
public final class NativeHistIdentityEventRow {

    private String sourceId;
    private String name;
    // entity + event core
    private String entityId;
    private String entityName;
    private String definedEntityName;
    private String eventType;
    private String eventCategory;
    private String eventSourceType;
    private Instant eventDate;
    private String eventDetailJson;
    private String prevCaptureId;
    private String captureId;
    private String auditEventId;
    // property change (QueryableAccessHistory)
    private String propertyName;
    private String oldValue;
    private String newValue;
    // account / application coordinates
    private String accountId;
    private String acctAppId;
    private String acctAppName;
    private String acctAppInstance;
    private String acctDisplayName;
    private String acctNativeId;
    // governance references (explicit ids)
    private String pendingRequestItemId;
    private String requestItemId;
    private String identityRequestId;
    private String identityEntitlementId;
    // source query columns (semantics are event-type specific; kept verbatim)
    private String qryProperty1;
    private String qryProperty2;
    private String qryProperty3;
    private String qryProperty4;
    private String qryProperty5;
    private String qryProperty6;
    private String qryProperty7;
    private String qryProperty8;
    private String qryProperty9;
    private String qryProperty10;
    private Instant created;
    private Instant modified;

    // lineage
    private String srcSystem;
    private final String srcInterface = "native_iiq_java_api";
    private String srcObjectType = "sailpoint.object.accesshistory.HistoricalIdentityEvent";
    private String extractionRunId;
    private Instant extractedAt;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String v) { this.sourceId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String v) { this.entityId = v; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String v) { this.entityName = v; }
    public String getDefinedEntityName() { return definedEntityName; }
    public void setDefinedEntityName(String v) { this.definedEntityName = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getEventCategory() { return eventCategory; }
    public void setEventCategory(String v) { this.eventCategory = v; }
    public String getEventSourceType() { return eventSourceType; }
    public void setEventSourceType(String v) { this.eventSourceType = v; }
    public Instant getEventDate() { return eventDate; }
    public void setEventDate(Instant v) { this.eventDate = v; }
    public String getEventDetailJson() { return eventDetailJson; }
    public void setEventDetailJson(String v) { this.eventDetailJson = v; }
    public String getPrevCaptureId() { return prevCaptureId; }
    public void setPrevCaptureId(String v) { this.prevCaptureId = v; }
    public String getCaptureId() { return captureId; }
    public void setCaptureId(String v) { this.captureId = v; }
    public String getAuditEventId() { return auditEventId; }
    public void setAuditEventId(String v) { this.auditEventId = v; }
    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String v) { this.propertyName = v; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String v) { this.oldValue = v; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String v) { this.newValue = v; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String v) { this.accountId = v; }
    public String getAcctAppId() { return acctAppId; }
    public void setAcctAppId(String v) { this.acctAppId = v; }
    public String getAcctAppName() { return acctAppName; }
    public void setAcctAppName(String v) { this.acctAppName = v; }
    public String getAcctAppInstance() { return acctAppInstance; }
    public void setAcctAppInstance(String v) { this.acctAppInstance = v; }
    public String getAcctDisplayName() { return acctDisplayName; }
    public void setAcctDisplayName(String v) { this.acctDisplayName = v; }
    public String getAcctNativeId() { return acctNativeId; }
    public void setAcctNativeId(String v) { this.acctNativeId = v; }
    public String getPendingRequestItemId() { return pendingRequestItemId; }
    public void setPendingRequestItemId(String v) { this.pendingRequestItemId = v; }
    public String getRequestItemId() { return requestItemId; }
    public void setRequestItemId(String v) { this.requestItemId = v; }
    public String getIdentityRequestId() { return identityRequestId; }
    public void setIdentityRequestId(String v) { this.identityRequestId = v; }
    public String getIdentityEntitlementId() { return identityEntitlementId; }
    public void setIdentityEntitlementId(String v) { this.identityEntitlementId = v; }
    public String getQryProperty1() { return qryProperty1; }
    public void setQryProperty1(String v) { this.qryProperty1 = v; }
    public String getQryProperty2() { return qryProperty2; }
    public void setQryProperty2(String v) { this.qryProperty2 = v; }
    public String getQryProperty3() { return qryProperty3; }
    public void setQryProperty3(String v) { this.qryProperty3 = v; }
    public String getQryProperty4() { return qryProperty4; }
    public void setQryProperty4(String v) { this.qryProperty4 = v; }
    public String getQryProperty5() { return qryProperty5; }
    public void setQryProperty5(String v) { this.qryProperty5 = v; }
    public String getQryProperty6() { return qryProperty6; }
    public void setQryProperty6(String v) { this.qryProperty6 = v; }
    public String getQryProperty7() { return qryProperty7; }
    public void setQryProperty7(String v) { this.qryProperty7 = v; }
    public String getQryProperty8() { return qryProperty8; }
    public void setQryProperty8(String v) { this.qryProperty8 = v; }
    public String getQryProperty9() { return qryProperty9; }
    public void setQryProperty9(String v) { this.qryProperty9 = v; }
    public String getQryProperty10() { return qryProperty10; }
    public void setQryProperty10(String v) { this.qryProperty10 = v; }
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
