package com.keyforge.nativeload;

import java.time.Instant;

/** One native AuditEvent row destined for {@code iiq_native.kf_audit_event} (append-only). Data holder. */
public final class NativeAuditEventRecord {

    String sourceId;
    String action;
    String auditSource;
    String target;
    String application;
    String accountName;
    String instance;
    String attributeName;
    String attributeValue;
    String interfaceName;
    String serverHost;
    String clientHost;
    String trackingId;
    String string1;
    String string2;
    String string3;
    String string4;
    String attributesJson;
    Instant created;
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getAction() {
        return action;
    }
}
