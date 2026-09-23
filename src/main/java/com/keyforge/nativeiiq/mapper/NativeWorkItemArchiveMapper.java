package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeWorkItemArchiveRow;
import com.keyforge.nativeiiq.wire.JsonSafe;

import sailpoint.object.Attributes;
import sailpoint.object.Comment;
import sailpoint.object.SignOffHistory;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a native {@code sailpoint.object.WorkItemArchive} into a {@link NativeWorkItemArchiveRow}. Read-only:
 * only getters verified against the real 8.4 {@code identityiq.jar}. Nothing is inferred — a native
 * {@code null} stays {@code null}. Nested collections (sign-offs, comments, owner history) are flattened to
 * plain JSON-safe maps from their documented getters; the free-form {@code Attributes} maps go through
 * {@link JsonSafe} so no Hibernate proxy or unexpected type escapes the extraction boundary.
 *
 * <p><b>CEC semantics:</b> {@code getId()} is the canonical stable identity of the archive (the source PK),
 * and {@code getArchived()} is the authoritative historical archival timestamp, carried as
 * {@code srcEventTs}. These records are immutable once written.
 */
public final class NativeWorkItemArchiveMapper {

    private NativeWorkItemArchiveMapper() {
    }

    public static NativeWorkItemArchiveRow map(WorkItemArchive a, String sourceSystem, String extractionRunId) {
        NativeWorkItemArchiveRow row = new NativeWorkItemArchiveRow();

        row.setSourceId(a.getId());
        row.setWorkItemId(a.getWorkItemId());
        row.setName(a.getName());
        row.setType(enumName(a.getType()));
        row.setState(enumName(a.getState()));
        row.setLevel(enumName(a.getLevel()));

        row.setRequester(a.getRequester());
        row.setAssignee(a.getAssignee());
        row.setOwnerName(a.getOwnerName());
        row.setCompleter(a.getCompleter());
        row.setCompletionComments(a.getCompletionComments());
        row.setSigned(Boolean.valueOf(a.isSigned()));

        row.setTargetClass(a.getTargetClass());
        row.setTargetId(a.getTargetId());
        row.setTargetName(a.getTargetName());

        row.setIdentityRequestId(a.getIdentityRequestId());
        row.setCertificationId(a.getCertification());
        row.setCertificationEntityId(a.getCertificationEntity());
        row.setCertificationItemId(a.getCertificationItem());
        row.setEntityType(enumName(a.getEntityType()));

        mapSignOffs(a.getSignOffs(), row);
        mapComments(a.getComments(), row);
        mapOwnerHistory(a.getOwnerHistory(), row);
        putSafe(a.getSystemAttributes(), row.getSystemAttributes());
        putSafe(a.getAttributes(), row.getAttributes());

        row.setCreated(toInstant(a.getCreated()));
        row.setModified(toInstant(a.getModified()));
        row.setExpiration(toInstant(a.getExpiration()));
        Instant archived = toInstant(a.getArchived());
        row.setArchived(archived);

        row.setSrcSystem(sourceSystem);
        row.setSrcNaturalKey(a.getName());
        row.setSrcEventTs(archived);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static void mapSignOffs(List<SignOffHistory> signOffs, NativeWorkItemArchiveRow row) {
        if (signOffs == null) {
            return;
        }
        for (SignOffHistory s : signOffs) {
            if (s == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("signerId", s.getSignerId());
            m.put("signerName", s.getSignerName());
            m.put("signerDisplayName", s.getSignerDisplayName());
            m.put("date", iso(s.getDate()));
            m.put("application", s.getApplication());
            m.put("account", s.getAccount());
            m.put("text", s.getText());
            m.put("electronicSign", Boolean.valueOf(s.isElectronicSign()));
            row.getSignOffs().add(m);
        }
    }

    private static void mapComments(List<Comment> comments, NativeWorkItemArchiveRow row) {
        if (comments == null) {
            return;
        }
        for (Comment c : comments) {
            if (c == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("author", c.getAuthor());
            m.put("comment", c.getComment());
            m.put("date", iso(c.getDate()));
            row.getComments().add(m);
        }
    }

    private static void mapOwnerHistory(List<WorkItem.OwnerHistory> history, NativeWorkItemArchiveRow row) {
        if (history == null) {
            return;
        }
        for (WorkItem.OwnerHistory h : history) {
            if (h == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("oldOwner", h.getOldOwner());
            m.put("oldOwnerDisplayName", h.getOldOwnerDisplayName());
            m.put("newOwner", h.getNewOwner());
            m.put("newOwnerDisplayName", h.getNewOwnerDisplayName());
            m.put("source", h.getSource());
            m.put("comment", h.getComment());
            m.put("startDate", iso(h.getStartDate()));
            row.getOwnerHistory().add(m);
        }
    }

    /** Degrades the free-form attribute map into JSON-safe values (never throws, no proxy escapes). */
    @SuppressWarnings("unchecked")
    private static void putSafe(Attributes<String, Object> attrs, Map<String, Object> out) {
        if (attrs == null) {
            return;
        }
        Object safe = JsonSafe.toJsonSafe(attrs);
        if (safe instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) safe).entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String iso(Date d) {
        return d == null ? null : d.toInstant().toString();
    }

    private static Instant toInstant(Date d) {
        return d == null ? null : d.toInstant();
    }
}
