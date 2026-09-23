package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeProvisioningItemRow;
import com.keyforge.nativeiiq.model.NativeProvisioningTxnExtractionResult;
import com.keyforge.nativeiiq.model.NativeProvisioningTxnRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON wire envelope for native ProvisioningTransaction (with nested derived items). */
public final class NativeProvisioningTxnWire {

    private NativeProvisioningTxnWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "ProvisioningTransaction");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeProvisioningTxnExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeProvisioningTxnRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "ProvisioningTransaction");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeProvisioningTxnRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("operation", r.getOperation());
        m.put("type", r.getType());
        m.put("status", r.getStatus());
        m.put("source", r.getSource());
        m.put("integration", r.getIntegration());
        m.put("forced", r.getForced());
        m.put("identityName", r.getIdentityName());
        m.put("identityDisplayName", r.getIdentityDisplayName());
        m.put("applicationName", r.getApplicationName());
        m.put("nativeIdentity", r.getNativeIdentity());
        m.put("accountDisplayName", r.getAccountDisplayName());
        m.put("certificationId", r.getCertificationId());
        m.put("certificationName", r.getCertificationName());
        m.put("accessRequestId", r.getAccessRequestId());
        m.put("waitWorkItemId", r.getWaitWorkItemId());
        m.put("manualWorkItemId", r.getManualWorkItemId());
        m.put("ticketId", r.getTicketId());
        m.put("retryRequestId", r.getRetryRequestId());
        m.put("lastRetry", iso(r.getLastRetry()));
        m.put("retryCount", r.getRetryCount());
        m.put("timedOut", r.getTimedOut());
        m.put("filtered", r.getFiltered());
        m.put("planResultStatus", r.getPlanResultStatus());
        m.put("planResultRequestId", r.getPlanResultRequestId());
        m.put("planResultErrors", new ArrayList<String>(r.getPlanResultErrors()));
        m.put("accountRequestOperation", r.getAccountRequestOperation());
        m.put("requestId", r.getRequestId());
        m.put("itemCount", Integer.valueOf(r.getItems().size()));
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (NativeProvisioningItemRow i : r.getItems()) {
            items.add(item(i));
        }
        m.put("items", items);
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

    static Map<String, Object> item(NativeProvisioningItemRow i) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("txnSourceId", i.getTxnSourceId());
        m.put("identityName", i.getIdentityName());
        m.put("itemType", i.getItemType());
        m.put("operation", i.getOperation());
        m.put("applicationName", i.getApplicationName());
        m.put("nativeIdentity", i.getNativeIdentity());
        m.put("instance", i.getInstance());
        m.put("accountOperation", i.getAccountOperation());
        m.put("name", i.getName());
        m.put("value", i.getValue());
        m.put("valueJson", i.getValueJson());
        m.put("assignmentId", i.getAssignmentId());
        m.put("assignment", i.getAssignment());
        m.put("permissionTarget", i.getPermissionTarget());
        m.put("permissionRights", i.getPermissionRights());
        m.put("requestId", i.getRequestId());
        m.put("itemIndex", Integer.valueOf(i.getItemIndex()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
