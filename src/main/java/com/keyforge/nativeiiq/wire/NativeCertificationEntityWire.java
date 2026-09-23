package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeCertificationEntityExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationEntityRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON wire envelope for native CertificationEntity. Field names are the contract for NativeCertificationEntityFields. */
public final class NativeCertificationEntityWire {

    private NativeCertificationEntityWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "CertificationEntity");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeCertificationEntityExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeCertificationEntityRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "CertificationEntity");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeCertificationEntityRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("certificationId", r.getCertificationId());
        m.put("identity", r.getIdentity());
        m.put("application", r.getApplication());
        m.put("nativeIdentity", r.getNativeIdentity());
        m.put("accountGroup", r.getAccountGroup());
        m.put("firstName", r.getFirstName());
        m.put("lastName", r.getLastName());
        m.put("fullName", r.getFullName());
        m.put("referenceAttribute", r.getReferenceAttribute());
        m.put("schemaObjectType", r.getSchemaObjectType());
        m.put("snapshotId", r.getSnapshotId());
        m.put("pendingCertification", r.getPendingCertification());
        m.put("type", r.getType());
        m.put("summaryStatus", r.getSummaryStatus());
        m.put("entityDelegated", r.getEntityDelegated());
        m.put("entityDelegationStatus", r.getEntityDelegationStatus());
        m.put("compositeScore", r.getCompositeScore());
        m.put("targetId", r.getTargetId());
        m.put("targetName", r.getTargetName());
        m.put("targetDisplayName", r.getTargetDisplayName());
        m.put("completed", iso(r.getCompleted()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("actionStatus", r.getActionStatus());
        m.put("actionDecisionDate", iso(r.getActionDecisionDate()));
        m.put("actionRemediationAction", r.getActionRemediationAction());
        m.put("actionActorName", r.getActionActorName());
        m.put("actionActorDisplayName", r.getActionActorDisplayName());
        m.put("actionComments", r.getActionComments());
        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
