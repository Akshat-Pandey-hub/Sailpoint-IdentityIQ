package com.keyforge.iiq.model;

/**
 * A SailPoint IdentityIQ <b>Work Item</b> as returned by the classic UI REST endpoint
 * {@code POST /identityiq/ui/rest/workItems/}. Only the fields the endpoint actually
 * returns are modelled; each is a scalar or a single {@link Ref} reference (owner /
 * requester / assignee / target) — there are no arrays or arbitrary bags in this
 * response, so it maps to a flat table with no JSON columns.
 *
 * <p>Timestamps ({@code created}, {@code notificationDate}, {@code expirationDate},
 * {@code wakeUpDate}) are epoch-millis {@link Long}s as the source sends them.
 */
public final class WorkItem {

    private final String id;
    private final String workItemName;
    private final String workItemType;
    private final String workItemState;
    private final String accessRequestName;
    private final String description;
    private final String priority;
    private final Integer commentCount;
    private final Boolean editable;
    private final Long created;
    private final Long notificationDate;
    private final Long expirationDate;
    private final Long wakeUpDate;
    private final Integer reminders;
    private final Integer escalationCount;
    private final String completionComments;
    private final String esigMeaning;
    private final String certificationId;
    private final Boolean disableForwarding;
    private final Boolean forceClassicApprovalUI;
    private final Boolean newTypeWorkItem;
    private final Ref owner;
    private final Ref requester;
    private final Ref assignee;
    private final Ref target;

    public WorkItem(String id, String workItemName, String workItemType, String workItemState,
                    String accessRequestName, String description, String priority, Integer commentCount,
                    Boolean editable, Long created, Long notificationDate, Long expirationDate, Long wakeUpDate,
                    Integer reminders, Integer escalationCount, String completionComments, String esigMeaning,
                    String certificationId, Boolean disableForwarding, Boolean forceClassicApprovalUI,
                    Boolean newTypeWorkItem, Ref owner, Ref requester, Ref assignee, Ref target) {
        this.id = id;
        this.workItemName = workItemName;
        this.workItemType = workItemType;
        this.workItemState = workItemState;
        this.accessRequestName = accessRequestName;
        this.description = description;
        this.priority = priority;
        this.commentCount = commentCount;
        this.editable = editable;
        this.created = created;
        this.notificationDate = notificationDate;
        this.expirationDate = expirationDate;
        this.wakeUpDate = wakeUpDate;
        this.reminders = reminders;
        this.escalationCount = escalationCount;
        this.completionComments = completionComments;
        this.esigMeaning = esigMeaning;
        this.certificationId = certificationId;
        this.disableForwarding = disableForwarding;
        this.forceClassicApprovalUI = forceClassicApprovalUI;
        this.newTypeWorkItem = newTypeWorkItem;
        this.owner = owner;
        this.requester = requester;
        this.assignee = assignee;
        this.target = target;
    }

    public String getId() {
        return id;
    }

    public String getWorkItemName() {
        return workItemName;
    }

    public String getWorkItemType() {
        return workItemType;
    }

    public String getWorkItemState() {
        return workItemState;
    }

    public String getAccessRequestName() {
        return accessRequestName;
    }

    public String getDescription() {
        return description;
    }

    public String getPriority() {
        return priority;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public Boolean getEditable() {
        return editable;
    }

    public Long getCreated() {
        return created;
    }

    public Long getNotificationDate() {
        return notificationDate;
    }

    public Long getExpirationDate() {
        return expirationDate;
    }

    public Long getWakeUpDate() {
        return wakeUpDate;
    }

    public Integer getReminders() {
        return reminders;
    }

    public Integer getEscalationCount() {
        return escalationCount;
    }

    public String getCompletionComments() {
        return completionComments;
    }

    public String getEsigMeaning() {
        return esigMeaning;
    }

    public String getCertificationId() {
        return certificationId;
    }

    public Boolean getDisableForwarding() {
        return disableForwarding;
    }

    public Boolean getForceClassicApprovalUI() {
        return forceClassicApprovalUI;
    }

    public Boolean getNewTypeWorkItem() {
        return newTypeWorkItem;
    }

    public Ref getOwner() {
        return owner;
    }

    public Ref getRequester() {
        return requester;
    }

    public Ref getAssignee() {
        return assignee;
    }

    public Ref getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "WorkItem{id=" + id + ", name=" + workItemName + ", type=" + workItemType + "}";
    }

    /**
     * A reference to another IdentityIQ object (identity/account) carried by a work
     * item: its {@code id}, its {@code name}, and a human {@code displayName}.
     */
    public static final class Ref {
        private final String id;
        private final String name;
        private final String displayName;

        public Ref(String id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
