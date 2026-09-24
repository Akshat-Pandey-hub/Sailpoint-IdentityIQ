package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeSyslogEventRow;

import sailpoint.object.SyslogEvent;

import java.time.Instant;
import java.util.Date;

/**
 * Maps a native {@code sailpoint.object.SyslogEvent} into a {@link NativeSyslogEventRow}. Read-only; only
 * getters verified against the 8.4 {@code identityiq.jar}. {@code username} is a raw actor name kept
 * verbatim — never resolved to an entity link. Nothing is inferred.
 */
public final class NativeSyslogEventMapper {

    private NativeSyslogEventMapper() {
    }

    public static NativeSyslogEventRow map(SyslogEvent s, String sourceSystem, String extractionRunId) {
        NativeSyslogEventRow row = new NativeSyslogEventRow();

        row.setSourceId(s.getId());
        row.setQuickKey(s.getQuickKey());
        row.setEventLevel(s.getEventLevel());
        row.setServer(s.getServer());
        row.setUsername(s.getUsername());
        row.setThread(s.getThread());
        row.setLineNumber(s.getLineNumber());
        row.setMessage(s.getMessage());
        row.setStacktrace(s.getStacktrace());
        row.setCreated(toInstant(s.getCreated()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
