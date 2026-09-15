package com.keyforge.iiq.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A single Account &rarr; Entitlement assignment, derived (not fetched) from the
 * already-extracted Accounts and Entitlements (and, where available, the SailPoint
 * User-extension entitlement references).
 *
 * <p>One instance represents exactly one native value held by one account, e.g.
 * an account whose {@code groups} attribute is {@code ["A","B","C"]} yields three
 * assignments. Multiple values are never collapsed into one.
 *
 * <p>This object is intentionally shaped to feed the target PostgreSQL
 * {@code entitlementassignment} table, but only carries what IdentityIQ actually
 * provides. Fields IIQ does not expose are represented as {@code null}/absent and
 * are documented as such — they are never fabricated. In particular
 * {@code provisioningmechanism} is always {@code null}: IIQ SCIM exposes no
 * provisioning-source information, so the Request ID / Target Recon / Direct
 * classification is deferred to a separate, future task.
 */
public final class AccountEntitlementAssignment {

    /** Whether the native value was resolved to a catalogue Entitlement id. */
    public enum ResolutionStatus {
        RESOLVED,
        UNRESOLVED
    }

    /** Where the evidence for this assignment came from. */
    public enum ResolutionSource {
        /** Derived from an account's application-specific attribute value. */
        ACCOUNT_ATTRIBUTE,
        /** Derived from / confirmed by the SailPoint User-extension entitlements. */
        USER_EXTENSION
    }

    // --- relationship anchors (Account -> Identity / Application) ------------
    private final String accountId;
    private final String accountName;
    private final String identityId;
    private final String identityDisplayName;
    private final String applicationId;
    private final String applicationName;

    // --- the specific assignment --------------------------------------------
    private final String sourceAttribute;   // discovered account attribute (e.g. "groups"); never hardcoded
    private final String entitlementValue;  // the native value (e.g. a GUID or DN)
    private final String entitlementId;     // resolved Entitlement id (target entitlementid), or null
    private final String entitlementDisplayName;
    private final String entitlementType;

    private final ResolutionStatus resolutionStatus;
    private final Set<ResolutionSource> resolutionSources;

    /** Lossless context for the target {@code customattributes} JSONB column; never null. */
    private final ObjectNode customAttributes;

    public AccountEntitlementAssignment(String accountId,
                                        String accountName,
                                        String identityId,
                                        String identityDisplayName,
                                        String applicationId,
                                        String applicationName,
                                        String sourceAttribute,
                                        String entitlementValue,
                                        String entitlementId,
                                        String entitlementDisplayName,
                                        String entitlementType,
                                        ResolutionStatus resolutionStatus,
                                        Set<ResolutionSource> resolutionSources,
                                        ObjectNode customAttributes) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.identityId = identityId;
        this.identityDisplayName = identityDisplayName;
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.sourceAttribute = sourceAttribute;
        this.entitlementValue = entitlementValue;
        this.entitlementId = entitlementId;
        this.entitlementDisplayName = entitlementDisplayName;
        this.entitlementType = entitlementType;
        this.resolutionStatus = resolutionStatus;
        this.resolutionSources = resolutionSources == null || resolutionSources.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(resolutionSources));
        this.customAttributes = customAttributes == null
                ? JsonNodeFactory.instance.objectNode()
                : customAttributes;
    }

    // --- relationship anchors -----------------------------------------------

    /** Target {@code entitlementassignment.accountid}. May be null for an
     *  unmatched User-extension entry (reported, never silently dropped). */
    public String getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    /** The correlated identity id (Account &rarr; Identity); may be null. */
    public String getIdentityId() {
        return identityId;
    }

    public String getIdentityDisplayName() {
        return identityDisplayName;
    }

    /** The owning application id (Account &rarr; Application); may be null. */
    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    // --- the assignment -----------------------------------------------------

    /** The account attribute the value came from (discovered from the catalogue). */
    public String getSourceAttribute() {
        return sourceAttribute;
    }

    public String getEntitlementValue() {
        return entitlementValue;
    }

    /** Target {@code entitlementassignment.entitlementid}; null when unresolved. */
    public String getEntitlementId() {
        return entitlementId;
    }

    public String getEntitlementDisplayName() {
        return entitlementDisplayName;
    }

    public String getEntitlementType() {
        return entitlementType;
    }

    public ResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public boolean isResolved() {
        return resolutionStatus == ResolutionStatus.RESOLVED;
    }

    public Set<ResolutionSource> getResolutionSources() {
        return resolutionSources;
    }

    public ObjectNode getCustomAttributes() {
        return customAttributes;
    }

    // --- target columns IdentityIQ does NOT provide -------------------------
    // These are exposed as always-null so the mapping layer sees the full target
    // shape without any value being fabricated. See class javadoc.

    /** Generated by the persistence layer, not by extraction. Always null here. */
    public String getAssignmentId() {
        return null;
    }

    /** Not provided by IIQ SCIM for account entitlements. Always null. */
    public String getAssignedDate() {
        return null;
    }

    /** Not provided by IIQ SCIM. Always null. */
    public String getExpirationDate() {
        return null;
    }

    /** Not provided by IIQ SCIM at assignment granularity. Always null. */
    public String getStatus() {
        return null;
    }

    /** Not provided by IIQ SCIM. Always null. */
    public String getAssignedBy() {
        return null;
    }

    /**
     * IIQ SCIM exposes no provisioning-source data, so this is never inferred.
     * Always null; the Request ID / Target Recon / Direct classification is a
     * separate future task.
     */
    public String getProvisioningMechanism() {
        return null;
    }

    /** Assigned by the migration run, not by extraction. Always null here. */
    public String getRunId() {
        return null;
    }

    /** Assigned by the persistence layer. Always null here. */
    public String getLastModifiedDate() {
        return null;
    }

    /** Assigned by the persistence layer. Always null here. */
    public String getLastModifiedBy() {
        return null;
    }

    /** Assigned by the persistence layer. Always null here. */
    public String getTenantId() {
        return null;
    }

    @Override
    public String toString() {
        return "AccountEntitlementAssignment{accountId=" + accountId
                + ", application=" + applicationName
                + ", sourceAttribute=" + sourceAttribute
                + ", entitlementValue=" + entitlementValue
                + ", entitlementId=" + entitlementId
                + ", status=" + resolutionStatus
                + ", sources=" + resolutionSources
                + "}";
    }
}
