package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeCertificationItemExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationItemRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON wire envelope for native CertificationItem. Contract for NativeCertificationItemFields. */
public final class NativeCertificationItemWire {

    private NativeCertificationItemWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "CertificationItem");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeCertificationItemExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeCertificationItemRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "CertificationItem");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeCertificationItemRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("certificationId", r.getCertificationId());
        m.put("entityId", r.getEntityId());
        m.put("identity", r.getIdentity());
        m.put("type", r.getType());
        m.put("subType", r.getSubType());
        m.put("bundle", r.getBundle());
        m.put("bundleAssignmentId", r.getBundleAssignmentId());
        m.put("exceptionApplication", r.getExceptionApplication());
        m.put("exceptionAttributeName", r.getExceptionAttributeName());
        m.put("exceptionAttributeValue", r.getExceptionAttributeValue());
        m.put("exceptionPermissionTarget", r.getExceptionPermissionTarget());
        m.put("exceptionPermissionRight", r.getExceptionPermissionRight());
        m.put("accountGroup", r.getAccountGroup());
        m.put("phase", r.getPhase());
        m.put("summaryStatus", r.getSummaryStatus());
        m.put("completed", iso(r.getCompleted()));
        m.put("lastDecision", iso(r.getLastDecision()));
        m.put("expirationDate", iso(r.getExpirationDate()));
        m.put("finishedDate", iso(r.getFinishedDate()));
        m.put("iiqElevatedAccess", r.getIiqElevatedAccess());
        m.put("reviewed", r.getReviewed());
        m.put("delegated", r.getDelegated());
        m.put("actedUpon", r.getActedUpon());
        m.put("historical", r.getHistorical());
        m.put("expired", r.getExpired());
        m.put("targetId", r.getTargetId());
        m.put("targetName", r.getTargetName());
        m.put("shortDescription", r.getShortDescription());
        m.put("violationSummary", r.getViolationSummary());
        m.put("applicationNames", new ArrayList<String>(r.getApplicationNames()));
        m.put("classificationNames", new ArrayList<String>(r.getClassificationNames()));
        m.put("actionStatus", r.getActionStatus());
        m.put("actionDecisionDate", iso(r.getActionDecisionDate()));
        m.put("actionDecisionCertificationId", r.getActionDecisionCertificationId());
        m.put("actionRemediationAction", r.getActionRemediationAction());
        m.put("actionActorName", r.getActionActorName());
        m.put("actionActorDisplayName", r.getActionActorDisplayName());
        m.put("actionComments", r.getActionComments());
        m.put("actionCompletionComments", r.getActionCompletionComments());
        m.put("actionOwnerName", r.getActionOwnerName());
        m.put("actionMitigationExpiration", iso(r.getActionMitigationExpiration()));
        m.put("actionIsApproved", r.getActionIsApproved());
        m.put("actionIsRemediation", r.getActionIsRemediation());
        m.put("actionIsMitigation", r.getActionIsMitigation());
        m.put("actionIsDelegation", r.getActionIsDelegation());
        m.put("actionIsRevokeAccount", r.getActionIsRevokeAccount());
        m.put("actionIsAutoDecision", r.getActionIsAutoDecision());
        m.put("actionIsBulkCertified", r.getActionIsBulkCertified());
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
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
