package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeCertificationExtractionResult;
import com.keyforge.nativeiiq.model.NativeCertificationRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON wire envelope for native Certification. Field names are the contract for NativeCertificationFields. */
public final class NativeCertificationWire {

    private NativeCertificationWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Certification");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeCertificationExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeCertificationRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "Certification");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeCertificationRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("certificationName", r.getCertificationName());
        m.put("shortName", r.getShortName());
        m.put("type", r.getType());
        m.put("phase", r.getPhase());
        m.put("comments", r.getComments());
        m.put("creator", r.getCreator());
        m.put("manager", r.getManager());
        m.put("certificationGroupId", r.getCertificationGroupId());
        m.put("certificationGroupName", r.getCertificationGroupName());
        m.put("certificationDefinitionId", r.getCertificationDefinitionId());
        m.put("groupDefinitionId", r.getGroupDefinitionId());
        m.put("groupDefinitionName", r.getGroupDefinitionName());
        m.put("applicationId", r.getApplicationId());
        m.put("taskScheduleId", r.getTaskScheduleId());
        m.put("triggerId", r.getTriggerId());
        m.put("parentId", r.getParentId());
        m.put("complete", r.getComplete());
        m.put("expired", r.getExpired());
        m.put("continuous", r.getContinuous());
        m.put("electronicallySigned", r.getElectronicallySigned());
        m.put("signed", iso(r.getSigned()));
        m.put("finished", iso(r.getFinished()));
        m.put("activated", iso(r.getActivated()));
        m.put("expiration", iso(r.getExpiration()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("totalItems", r.getTotalItems());
        m.put("completedItems", r.getCompletedItems());
        m.put("openItems", r.getOpenItems());
        m.put("totalEntities", r.getTotalEntities());
        m.put("completedEntities", r.getCompletedEntities());
        m.put("openEntities", r.getOpenEntities());
        m.put("percentComplete", r.getPercentComplete());
        m.put("certifiers", new ArrayList<String>(r.getCertifiers()));
        m.put("signOffHistory", new ArrayList<Map<String, Object>>(r.getSignOffHistory()));
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("approverRule", r.getApproverRule());
        m.put("automaticClosingDate", r.getAutomaticClosingDate());
        m.put("allowedStatuses", r.getAllowedStatuses());
        m.put("tags", r.getTags());
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
