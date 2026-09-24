package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeAuditEventRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.AuditEvent;
import sailpoint.object.Attributes;

import java.time.Instant;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Maps a native {@code sailpoint.object.AuditEvent} into a {@link NativeAuditEventRow}. Read-only; only
 * getters verified against the 8.4 {@code identityiq.jar}. AuditEvent fields ({@code source}/{@code target}/
 * {@code application}) are raw audit strings kept verbatim — never resolved to authoritative entity links.
 * The free-form attribute map is name-pattern redacted for secrets and made JSON-safe.
 */
public final class NativeAuditEventMapper {

    private static final String REDACTED = "<redacted>";
    private static final Pattern SECRET_NAME =
            Pattern.compile("(?i).*(password|passwd|secret|token|credential|private[_ -]?key).*");

    private NativeAuditEventMapper() {
    }

    public static NativeAuditEventRow map(AuditEvent a, String sourceSystem, String extractionRunId) {
        NativeAuditEventRow row = new NativeAuditEventRow();

        row.setSourceId(a.getId());
        row.setAction(a.getAction());
        row.setAuditSource(a.getSource());
        row.setTarget(a.getTarget());
        row.setApplication(a.getApplication());
        row.setAccountName(a.getAccountName());
        row.setInstance(a.getInstance());
        row.setAttributeName(a.getAttributeName());
        row.setAttributeValue(a.getAttributeValue());
        row.setInterfaceName(a.getInterface());
        row.setServerHost(a.getServerHost());
        row.setClientHost(a.getClientHost());
        row.setTrackingId(a.getTrackingId());
        row.setString1(a.getString1());
        row.setString2(a.getString2());
        row.setString3(a.getString3());
        row.setString4(a.getString4());

        Attributes<String, Object> attrs = a.getAttributes();
        if (attrs != null) {
            for (Object key : attrs.keySet()) {
                if (key != null) {
                    String k = key.toString();
                    row.getAttributes().put(k,
                            SECRET_NAME.matcher(k).matches() ? REDACTED : JsonSafe.toJsonSafe(attrs.get(key)));
                }
            }
        }

        row.setCreated(toInstant(a.getCreated()));

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
