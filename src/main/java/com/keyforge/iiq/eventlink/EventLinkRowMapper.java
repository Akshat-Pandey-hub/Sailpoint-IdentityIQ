package com.keyforge.iiq.eventlink;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Maps an AuditEvent's (id, target) plus a {@link EventLinkResolver.Result} into a
 * {@link EventLinkRow}. Pure and DB-free.
 *
 * <p>The PK is deterministic per audit event ({@code kf_event_link|AUDIT_TARGET|<auditid>}): there
 * is exactly one audit-target link per event, so a rerun upserts the same row and never duplicates.
 * The raw target is preserved verbatim; the resolved object type/id are carried only when the
 * resolver returned {@link EventLinkResolver.Status#RESOLVED}.
 */
public final class EventLinkRowMapper {

    private static final String SOURCE_TYPE = "AuditEvent";
    private static final String LINK_DISCRIMINATOR = "AUDIT_TARGET";

    private EventLinkRowMapper() {
    }

    public static EventLinkRow map(String auditEventRawId, String rawTarget, EventLinkResolver.Result r) {
        String auditId = toCanonicalUuid(auditEventRawId);
        String id = UUID.nameUUIDFromBytes(
                ("kf_event_link|" + LINK_DISCRIMINATOR + "|" + auditId).getBytes(StandardCharsets.UTF_8))
                .toString();
        return new EventLinkRow(
                id,
                auditId,
                SOURCE_TYPE,
                r.targetType(),
                r.targetId(),
                rawTarget,
                r.typeHint(),
                r.status().name(),
                r.rule());
    }

    static String toCanonicalUuid(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new EventLinkMappingException("AuditEvent id is missing; cannot form a kf_event_link key.");
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
            String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
            return UUID.fromString(dashed).toString();
        }
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            throw new EventLinkMappingException("AuditEvent id '" + rawId + "' is not a valid PostgreSQL UUID.");
        }
    }
}
