package com.keyforge.nativeload;

import java.time.Instant;

/** One native SyslogEvent row destined for {@code iiq_native.kf_syslog_event} (append-only). Data holder. */
public final class NativeSyslogEventRecord {

    String sourceId;
    String quickKey;
    String eventLevel;
    String server;
    String username;
    String thread;
    String lineNumber;
    String message;
    String stacktrace;
    Instant created;
    String srcSystem;
    String srcInterface;
    String srcObjectType;
    String extractionRunId;
    Instant extractedAt;

    public String getSourceId() {
        return sourceId;
    }

    public String getEventLevel() {
        return eventLevel;
    }
}
