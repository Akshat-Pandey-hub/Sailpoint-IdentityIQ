package com.keyforge.nativeiiq.model;

import java.time.Instant;

/**
 * A reference to one native {@code sailpoint.object.MitigationExpiration} on an Identity — the
 * certification exception/mitigation provenance (an access exception temporarily allowed during
 * certification: who allowed it, until when, on which role / entitlement / policy). This governance
 * provenance is not represented by any other native table (the tool's certification tables are
 * campaign/item/decision-level, not the identity-side exception grant). Pure data holder. Fields map to
 * getters verified in the 8.4 jar: {@code getMitigator()} (Identity), {@code getExpiration()},
 * {@code getComments()}, {@code getAction()} (enum), {@code getLastActionDate()}, {@code getRoleName()},
 * {@code getPolicy()}, {@code getConstraintName()}, {@code getApplication()}, {@code getInstance()},
 * {@code getNativeIdentity()}, {@code getAttributeName()}, {@code getAttributeValue()},
 * {@code isPermission()}.
 */
public final class NativeMitigationExpirationRef {

    private final String mitigatorId;
    private final String mitigatorName;
    private final Instant expiration;
    private final String comments;
    private final String action;
    private final Instant lastActionDate;
    private final String roleName;
    private final String policy;
    private final String constraintName;
    private final String application;
    private final String instance;
    private final String nativeIdentity;
    private final String attributeName;
    private final String attributeValue;
    private final Boolean permission;

    public NativeMitigationExpirationRef(String mitigatorId, String mitigatorName, Instant expiration,
                                         String comments, String action, Instant lastActionDate, String roleName,
                                         String policy, String constraintName, String application, String instance,
                                         String nativeIdentity, String attributeName, String attributeValue,
                                         Boolean permission) {
        this.mitigatorId = mitigatorId;
        this.mitigatorName = mitigatorName;
        this.expiration = expiration;
        this.comments = comments;
        this.action = action;
        this.lastActionDate = lastActionDate;
        this.roleName = roleName;
        this.policy = policy;
        this.constraintName = constraintName;
        this.application = application;
        this.instance = instance;
        this.nativeIdentity = nativeIdentity;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.permission = permission;
    }

    public String getMitigatorId() { return mitigatorId; }
    public String getMitigatorName() { return mitigatorName; }
    public Instant getExpiration() { return expiration; }
    public String getComments() { return comments; }
    public String getAction() { return action; }
    public Instant getLastActionDate() { return lastActionDate; }
    public String getRoleName() { return roleName; }
    public String getPolicy() { return policy; }
    public String getConstraintName() { return constraintName; }
    public String getApplication() { return application; }
    public String getInstance() { return instance; }
    public String getNativeIdentity() { return nativeIdentity; }
    public String getAttributeName() { return attributeName; }
    public String getAttributeValue() { return attributeValue; }
    public Boolean getPermission() { return permission; }
}
