package com.keyforge.nativeiiq.wire;

import com.keyforge.nativeiiq.model.NativeIdentityRequestApprovalRow;
import com.keyforge.nativeiiq.model.NativeIdentityRequestExtractionResult;
import com.keyforge.nativeiiq.model.NativeIdentityRequestItemRow;
import com.keyforge.nativeiiq.model.NativeIdentityRequestRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON wire envelope for native IdentityRequest (with nested items + approval summaries). */
public final class NativeIdentityRequestWire {

    private NativeIdentityRequestWire() {
    }

    public static Map<String, Object> errorEnvelope(int status, Throwable t) {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", t == null ? null : t.getClass().getName());
        error.put("message", t == null ? null : String.valueOf(t.getMessage()));
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "IdentityRequest");
        env.put("status", Integer.valueOf(status));
        env.put("error", error);
        return env;
    }

    public static Map<String, Object> envelope(NativeIdentityRequestExtractionResult result, int start, int limit) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (result != null) {
            for (NativeIdentityRequestRow r : result.getRows()) {
                rows.add(row(r));
            }
        }
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("entity", "IdentityRequest");
        env.put("sourceSystem", result == null ? null : result.getSourceSystem());
        env.put("extractionRunId", result == null ? null : result.getExtractionRunId());
        env.put("start", Integer.valueOf(start));
        env.put("limit", Integer.valueOf(limit));
        env.put("returned", Integer.valueOf(rows.size()));
        env.put("sourceCount", Integer.valueOf(result == null ? -1 : result.getSourceCount()));
        env.put("rows", rows);
        return env;
    }

    static Map<String, Object> row(NativeIdentityRequestRow r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", r.getSourceId());
        m.put("name", r.getName());
        m.put("type", r.getType());
        m.put("userFriendlyType", r.getUserFriendlyType());
        m.put("state", r.getState());
        m.put("source", r.getSource());
        m.put("sourceObject", r.getSourceObject());
        m.put("completionStatus", r.getCompletionStatus());
        m.put("executionStatus", r.getExecutionStatus());
        m.put("priority", r.getPriority());
        m.put("requesterId", r.getRequesterId());
        m.put("requesterDisplayName", r.getRequesterDisplayName());
        m.put("targetId", r.getTargetId());
        m.put("targetDisplayName", r.getTargetDisplayName());
        m.put("externalTicketId", r.getExternalTicketId());
        m.put("processId", r.getProcessId());
        m.put("taskResultId", r.getTaskResultId());
        m.put("executing", r.getExecuting());
        m.put("failure", r.getFailure());
        m.put("rejected", r.getRejected());
        m.put("successful", r.getSuccessful());
        m.put("terminated", r.getTerminated());
        m.put("incomplete", r.getIncomplete());
        m.put("iiqOnly", r.getIiqOnly());
        m.put("provisioningComplete", r.getProvisioningComplete());
        m.put("endDate", iso(r.getEndDate()));
        m.put("verified", iso(r.getVerified()));
        m.put("created", iso(r.getCreated()));
        m.put("modified", iso(r.getModified()));
        m.put("ownerId", r.getOwnerId());
        m.put("ownerName", r.getOwnerName());
        m.put("errors", new ArrayList<String>(r.getErrors()));
        m.put("itemCount", Integer.valueOf(r.getItems().size()));
        m.put("approvalCount", Integer.valueOf(r.getApprovals().size()));
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (NativeIdentityRequestItemRow i : r.getItems()) {
            items.add(item(i));
        }
        m.put("items", items);
        List<Map<String, Object>> approvals = new ArrayList<Map<String, Object>>();
        for (NativeIdentityRequestApprovalRow a : r.getApprovals()) {
            approvals.add(approval(a));
        }
        m.put("approvals", approvals);
        m.put("srcSystem", r.getSrcSystem());
        m.put("srcInterface", r.getSrcInterface());
        m.put("srcObjectType", r.getSrcObjectType());
        m.put("extractionRunId", r.getExtractionRunId());
        m.put("extractedAt", iso(r.getExtractedAt()));
        return m;
    }

    static Map<String, Object> item(NativeIdentityRequestItemRow i) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("sourceId", i.getSourceId());
        m.put("requestSourceId", i.getRequestSourceId());
        m.put("requestName", i.getRequestName());
        m.put("application", i.getApplication());
        m.put("attributeName", i.getAttributeName());
        m.put("attributeValue", i.getAttributeValue());
        m.put("operation", i.getOperation());
        m.put("managedAttributeType", i.getManagedAttributeType());
        m.put("assignmentId", i.getAssignmentId());
        m.put("nativeIdentity", i.getNativeIdentity());
        m.put("instance", i.getInstance());
        m.put("approverName", i.getApproverName());
        m.put("approvalState", i.getApprovalState());
        m.put("approved", i.getApproved());
        m.put("approvalComplete", i.getApprovalComplete());
        m.put("rejected", i.getRejected());
        m.put("provisioningState", i.getProvisioningState());
        m.put("provisioningEngine", i.getProvisioningEngine());
        m.put("provisioningRequestId", i.getProvisioningRequestId());
        m.put("provisioningComplete", i.getProvisioningComplete());
        m.put("provisioningFailed", i.getProvisioningFailed());
        m.put("compilationStatus", i.getCompilationStatus());
        m.put("ownerName", i.getOwnerName());
        m.put("requesterComments", i.getRequesterComments());
        m.put("expansion", i.getExpansion());
        m.put("expansionCause", i.getExpansionCause());
        m.put("expansionInfo", i.getExpansionInfo());
        m.put("retries", i.getRetries());
        m.put("iiq", i.getIiq());
        m.put("startDate", iso(i.getStartDate()));
        m.put("endDate", iso(i.getEndDate()));
        m.put("created", iso(i.getCreated()));
        m.put("modified", iso(i.getModified()));
        return m;
    }

    static Map<String, Object> approval(NativeIdentityRequestApprovalRow a) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("requestSourceId", a.getRequestSourceId());
        m.put("requestName", a.getRequestName());
        m.put("workItemId", a.getWorkItemId());
        m.put("workItemType", a.getWorkItemType());
        m.put("owner", a.getOwner());
        m.put("ownerId", a.getOwnerId());
        m.put("completer", a.getCompleter());
        m.put("approved", a.getApproved());
        m.put("state", a.getState());
        m.put("stateKey", a.getStateKey());
        m.put("typeKey", a.getTypeKey());
        m.put("startDate", iso(a.getStartDate()));
        m.put("endDate", iso(a.getEndDate()));
        m.put("approvalItemCount", a.getApprovalItemCount());
        m.put("approvalIndex", Integer.valueOf(a.getApprovalIndex()));
        m.put("comments", new ArrayList<Map<String, Object>>(a.getComments()));
        m.put("signOff", new LinkedHashMap<String, Object>(a.getSignOff()));
        return m;
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
