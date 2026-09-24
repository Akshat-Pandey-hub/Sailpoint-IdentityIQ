package com.keyforge.nativeiiq.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native-source projection of a request-time approval, from
 * {@code IdentityRequest.getApprovalSummaries()} ({@code WorkflowSummary$ApprovalSummary}). This is the
 * <b>actual approval evidence</b> for the request (approver, decision, work item, comments, sign-off) — not
 * static workflow configuration. {@code workItemId} is preserved as the join to {@code kf_workitem} /
 * {@code kf_workitem_archive}. Pure data holder.
 */
public final class NativeIdentityRequestApprovalRow {

    private String requestSourceId;   // parent IdentityRequest.getId()
    private String requestName;
    private String workItemId;        // link to the (live or archived) work item
    private String workItemType;      // WorkItem.Type name
    private String owner;             // approver name
    private String ownerId;           // approver id
    private String completer;
    private Boolean approved;
    private String state;             // WorkItem.State name
    private String stateKey;
    private String typeKey;
    private Instant startDate;
    private Instant endDate;
    private Integer approvalItemCount;
    private int approvalIndex;        // disambiguator within a request

    private final List<Map<String, Object>> comments = new ArrayList<Map<String, Object>>();
    private final Map<String, Object> signOff = new LinkedHashMap<String, Object>();

    public String getRequestSourceId() { return requestSourceId; }
    public void setRequestSourceId(String v) { this.requestSourceId = v; }
    public String getRequestName() { return requestName; }
    public void setRequestName(String v) { this.requestName = v; }
    public String getWorkItemId() { return workItemId; }
    public void setWorkItemId(String v) { this.workItemId = v; }
    public String getWorkItemType() { return workItemType; }
    public void setWorkItemType(String v) { this.workItemType = v; }
    public String getOwner() { return owner; }
    public void setOwner(String v) { this.owner = v; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String v) { this.ownerId = v; }
    public String getCompleter() { return completer; }
    public void setCompleter(String v) { this.completer = v; }
    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean v) { this.approved = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getStateKey() { return stateKey; }
    public void setStateKey(String v) { this.stateKey = v; }
    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String v) { this.typeKey = v; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant v) { this.startDate = v; }
    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant v) { this.endDate = v; }
    public Integer getApprovalItemCount() { return approvalItemCount; }
    public void setApprovalItemCount(Integer v) { this.approvalItemCount = v; }
    public int getApprovalIndex() { return approvalIndex; }
    public void setApprovalIndex(int v) { this.approvalIndex = v; }
    public List<Map<String, Object>> getComments() { return comments; }
    public Map<String, Object> getSignOff() { return signOff; }
}
