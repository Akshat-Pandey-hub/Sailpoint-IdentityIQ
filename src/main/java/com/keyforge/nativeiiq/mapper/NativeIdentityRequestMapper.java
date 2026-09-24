package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeIdentityRequestApprovalRow;
import com.keyforge.nativeiiq.model.NativeIdentityRequestItemRow;
import com.keyforge.nativeiiq.model.NativeIdentityRequestRow;

import sailpoint.object.ApprovalSet;
import sailpoint.object.Comment;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.SignOffHistory;
import sailpoint.object.WorkflowSummary;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a native {@code sailpoint.object.IdentityRequest} into a {@link NativeIdentityRequestRow}, deriving
 * its items ({@code getItems()}) and its request-time approval evidence ({@code getApprovalSummaries()} →
 * {@code WorkflowSummary.ApprovalSummary}). Read-only; only getters verified against the 8.4
 * {@code identityiq.jar}. Message text is read via {@code getKey()} to avoid the {@code openconnector}
 * transitive dependency that {@code getLocalizedMessage()} pulls in. Nothing is inferred.
 */
public final class NativeIdentityRequestMapper {

    private NativeIdentityRequestMapper() {
    }

    public static NativeIdentityRequestRow map(IdentityRequest r, String sourceSystem, String extractionRunId) {
        NativeIdentityRequestRow row = new NativeIdentityRequestRow();

        row.setSourceId(r.getId());
        row.setName(r.getName());
        row.setType(r.getType());
        row.setUserFriendlyType(r.getUserFriendlyType());
        row.setState(r.getState());
        row.setSource(r.getSource());
        row.setSourceObject(enumName(r.getSourceObject()));
        row.setCompletionStatus(enumName(r.getCompletionStatus()));
        row.setExecutionStatus(enumName(r.getExecutionStatus()));
        row.setPriority(enumName(r.getPriority()));
        row.setRequesterId(r.getRequesterId());
        row.setRequesterDisplayName(r.getRequesterDisplayName());
        row.setTargetId(r.getTargetId());
        row.setTargetDisplayName(r.getTargetDisplayName());
        row.setExternalTicketId(r.getExternalTicketId());
        row.setProcessId(r.getProcessId());
        row.setTaskResultId(r.getTaskResultId());

        row.setExecuting(Boolean.valueOf(r.isExecuting()));
        row.setFailure(Boolean.valueOf(r.isFailure()));
        row.setRejected(Boolean.valueOf(r.isRejected()));
        row.setSuccessful(Boolean.valueOf(r.isSuccessful()));
        row.setTerminated(Boolean.valueOf(r.isTerminated()));
        row.setIncomplete(Boolean.valueOf(r.isIncomplete()));
        row.setIiqOnly(Boolean.valueOf(r.isIIQOnlyRequest()));
        row.setProvisioningComplete(Boolean.valueOf(r.provisioningComplete()));

        row.setEndDate(toInstant(r.getEndDate()));
        row.setVerified(toInstant(r.getVerified()));
        row.setCreated(toInstant(r.getCreated()));
        row.setModified(toInstant(r.getModified()));

        Identity owner = r.getOwner();
        if (owner != null) {
            row.setOwnerId(owner.getId());
            row.setOwnerName(owner.getName());
        }
        addMessageKeys(r.getErrors(), row.getErrors());

        mapItems(r, row);
        mapApprovals(r, row);

        row.setSrcSystem(sourceSystem);
        row.setExtractionRunId(extractionRunId);
        row.setExtractedAt(Instant.now());
        return row;
    }

    private static void mapItems(IdentityRequest r, NativeIdentityRequestRow row) {
        List<IdentityRequestItem> items = r.getItems();
        if (items == null) {
            return;
        }
        for (IdentityRequestItem it : items) {
            if (it == null) {
                continue;
            }
            NativeIdentityRequestItemRow item = new NativeIdentityRequestItemRow();
            item.setSourceId(it.getId());
            item.setRequestSourceId(r.getId());
            item.setRequestName(r.getName());
            item.setApplication(it.getApplication());
            item.setAttributeName(it.getName());
            item.setAttributeValue(it.getStringValue());
            item.setOperation(it.getOperation());
            item.setManagedAttributeType(it.getManagedAttributeType());
            item.setAssignmentId(it.getAssignmentId());
            item.setNativeIdentity(it.getNativeIdentity());
            item.setInstance(it.getInstance());
            item.setApproverName(it.getApproverName());
            item.setApprovalState(enumName(it.getApprovalState()));
            item.setApproved(Boolean.valueOf(it.isApproved()));
            item.setApprovalComplete(Boolean.valueOf(it.isApprovalComplete()));
            item.setRejected(Boolean.valueOf(it.isRejected()));
            item.setProvisioningState(enumName(it.getProvisioningState()));
            item.setProvisioningEngine(it.getProvisioningEngine());
            item.setProvisioningRequestId(it.getProvisioningRequestId());
            item.setProvisioningComplete(Boolean.valueOf(it.isProvisioningComplete()));
            item.setProvisioningFailed(Boolean.valueOf(it.isProvisioningFailed()));
            item.setCompilationStatus(enumName(it.getCompilationStatus()));
            item.setOwnerName(it.getOwnerName());
            item.setRequesterComments(it.getRequesterComments());
            item.setExpansion(Boolean.valueOf(it.isExpansion()));
            item.setExpansionCause(enumName(it.getExpansionCause()));
            item.setExpansionInfo(it.getExpansionInfo());
            item.setRetries(Integer.valueOf(it.getRetries()));
            item.setIiq(Boolean.valueOf(it.isIIQ()));
            item.setStartDate(toInstant(it.getStartDate()));
            item.setEndDate(toInstant(it.getEndDate()));
            item.setCreated(toInstant(it.getCreated()));
            item.setModified(toInstant(it.getModified()));
            row.getItems().add(item);
        }
    }

    private static void mapApprovals(IdentityRequest r, NativeIdentityRequestRow row) {
        List<WorkflowSummary.ApprovalSummary> summaries = r.getApprovalSummaries();
        if (summaries == null) {
            return;
        }
        int index = 0;
        for (WorkflowSummary.ApprovalSummary s : summaries) {
            if (s == null) {
                continue;
            }
            NativeIdentityRequestApprovalRow a = new NativeIdentityRequestApprovalRow();
            a.setRequestSourceId(r.getId());
            a.setRequestName(r.getName());
            a.setWorkItemId(s.getWorkItemId());
            a.setWorkItemType(enumName(s.getWorkItemType()));
            a.setOwner(s.getOwner());
            a.setOwnerId(s.getOwnerId());
            a.setCompleter(s.getCompleter());
            a.setApproved(Boolean.valueOf(s.isApproved()));
            a.setState(enumName(s.getState()));
            a.setStateKey(s.getStateKey());
            a.setTypeKey(s.getTypeKey());
            a.setStartDate(toInstant(s.getStartDate()));
            a.setEndDate(toInstant(s.getEndDate()));
            a.setApprovalIndex(index++);
            ApprovalSet set = s.getApprovalSet();
            if (set != null && set.getItems() != null) {
                a.setApprovalItemCount(Integer.valueOf(set.getItems().size()));
            }
            List<Comment> comments = s.getComments();
            if (comments != null) {
                for (Comment c : comments) {
                    if (c != null) {
                        a.getComments().add(comment(c));
                    }
                }
            }
            SignOffHistory signOff = s.getSignOff();
            if (signOff != null) {
                a.getSignOff().put("signerName", signOff.getSignerName());
                a.getSignOff().put("signerDisplayName", signOff.getSignerDisplayName());
                a.getSignOff().put("date", iso(signOff.getDate()));
                a.getSignOff().put("text", signOff.getText());
            }
            row.getApprovals().add(a);
        }
    }

    private static Map<String, Object> comment(Comment c) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("author", c.getAuthor());
        m.put("comment", c.getComment());
        m.put("date", iso(c.getDate()));
        return m;
    }

    private static void addMessageKeys(List<?> messages, List<String> out) {
        if (messages == null) {
            return;
        }
        for (Object m : messages) {
            if (m == null) {
                continue;
            }
            try {
                Object key = m.getClass().getMethod("getKey").invoke(m);
                if (key != null) {
                    out.add(String.valueOf(key));
                }
            } catch (Throwable t) {
                // never fail the request over an unreadable message
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
