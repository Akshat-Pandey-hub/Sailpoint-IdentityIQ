package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeCertificationArchiveExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationArchiveRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link NativeCertificationArchiveExtractionResult} into a plain JSON-friendly structure (only Maps,
 * Lists, Strings, Booleans, Numbers) so it serializes deterministically. Pure Java (no SailPoint dep) but
 * bundled in the plugin. Field names are the wire contract consumed by
 * {@code com.keyforge.nativeload.NativeCertificationArchiveFields}.
 */
public final class NativeCertificationArchiveWire {

    private NativeCertificationArchiveWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "CertificationArchive");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeCertificationArchiveExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeCertificationArchiveRow r : result.getCertificationArchives()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "CertificationArchive");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? 0 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeCertificationArchiveRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("certificationId", r.getCertificationId());
        m.put("certificationGroupId", r.getCertificationGroupId());
        m.put("creatorName", r.getCreatorName());
        m.put("ownerName", r.getOwnerName());
        m.put("comments", r.getComments());
        m.put("childCertificationIds", new ArrayList<String>(r.getChildCertificationIds()));
        m.put("archiveXml", r.getArchiveXml());
        m.put("signed", iso(r.getSigned()));
        m.put("expiration", iso(r.getExpiration()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("srcNaturalKey", r.getSrcNaturalKey());
        m.put("srcEventTs", iso(r.getSrcEventTs()));
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
