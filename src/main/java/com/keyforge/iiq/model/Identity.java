package com.keyforge.iiq.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.List;

/**
 * A SailPoint IdentityIQ Identity (SCIM {@code User}), limited to the fields we
 * have actually observed on the live {@code /scim/v2/Users} response.
 *
 * <p>Observed/mapped fields:
 * <ul>
 *     <li>{@code id}          - SCIM resource id</li>
 *     <li>{@code userName}    - SCIM {@code userName}</li>
 *     <li>{@code displayName} - SCIM {@code displayName}</li>
 *     <li>{@code active}      - SCIM {@code active}</li>
 *     <li>{@code email}       - primary value from SCIM {@code emails}</li>
 *     <li>{@code firstName}   - SCIM {@code name.givenName}</li>
 *     <li>{@code lastName}    - SCIM {@code name.familyName}</li>
 * </ul>
 *
 * <p>In addition, the raw <b>account references</b> found on the User are preserved
 * verbatim as JSON nodes. Later migration steps need the Identity&rarr;Account
 * relationship, and preserving the raw structure avoids guessing at sub-attribute
 * names we have not yet confirmed against the live payload.
 *
 * <p>Likewise the raw <b>entitlement references</b> from the SailPoint User
 * extension ({@code ...:sailpoint:1.0:User.entitlements}) are preserved verbatim.
 * Each entry explicitly links the identity to an account, an application and an
 * entitlement ({@code $ref}); the Account&rarr;Entitlement assignment layer uses
 * them to resolve/validate assignments. This list is additive and defaults to
 * empty, so it does not change any previously verified extraction behaviour.
 */
public final class Identity {

    private final String id;
    private final String userName;
    private final String displayName;
    private final Boolean active;
    private final String email;
    private final String firstName;
    private final String lastName;

    /** Raw account reference nodes as returned by IIQ; never null. */
    private final List<JsonNode> accountReferences;

    /** Raw SailPoint User-extension entitlement reference nodes; never null. */
    private final List<JsonNode> entitlementReferences;

    /**
     * Non-mapped, non-structural attributes preserved verbatim (never null).
     * Whatever the User response carries beyond the typed fields above — SailPoint
     * or enterprise extension attributes, custom attributes — so they can be stored
     * losslessly (e.g. in {@code usr.customattributes}) without a dedicated column.
     */
    private final ObjectNode extendedAttributes;

    public Identity(String id,
                    String userName,
                    String displayName,
                    Boolean active,
                    String email,
                    String firstName,
                    String lastName,
                    List<JsonNode> accountReferences) {
        this(id, userName, displayName, active, email, firstName, lastName,
                accountReferences, Collections.emptyList(), null);
    }

    public Identity(String id,
                    String userName,
                    String displayName,
                    Boolean active,
                    String email,
                    String firstName,
                    String lastName,
                    List<JsonNode> accountReferences,
                    List<JsonNode> entitlementReferences) {
        this(id, userName, displayName, active, email, firstName, lastName,
                accountReferences, entitlementReferences, null);
    }

    public Identity(String id,
                    String userName,
                    String displayName,
                    Boolean active,
                    String email,
                    String firstName,
                    String lastName,
                    List<JsonNode> accountReferences,
                    List<JsonNode> entitlementReferences,
                    ObjectNode extendedAttributes) {
        this.id = id;
        this.userName = userName;
        this.displayName = displayName;
        this.active = active;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.accountReferences = accountReferences == null
                ? Collections.emptyList()
                : List.copyOf(accountReferences);
        this.entitlementReferences = entitlementReferences == null
                ? Collections.emptyList()
                : List.copyOf(entitlementReferences);
        this.extendedAttributes = extendedAttributes == null
                ? JsonNodeFactory.instance.objectNode()
                : extendedAttributes;
    }

    public String getId() {
        return id;
    }

    public String getUserName() {
        return userName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Boolean getActive() {
        return active;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    /** Unmodifiable list of raw account reference JSON nodes (possibly empty). */
    public List<JsonNode> getAccountReferences() {
        return accountReferences;
    }

    public int getAccountReferenceCount() {
        return accountReferences.size();
    }

    /**
     * Unmodifiable list of raw entitlement reference JSON nodes from the SailPoint
     * User extension (possibly empty). Each node explicitly links the identity to
     * an account/application/entitlement.
     */
    public List<JsonNode> getEntitlementReferences() {
        return entitlementReferences;
    }

    public int getEntitlementReferenceCount() {
        return entitlementReferences.size();
    }

    /**
     * Non-mapped, non-structural attributes preserved verbatim (never null; empty
     * when the response carried nothing beyond the typed fields). Excludes the
     * fields already mapped to typed getters, so it does not duplicate them.
     */
    public ObjectNode getExtendedAttributes() {
        return extendedAttributes;
    }

    /** Does not include email so that verification output stays low on PII. */
    @Override
    public String toString() {
        return "Identity{id=" + id
                + ", userName=" + userName
                + ", displayName=" + displayName
                + ", active=" + active
                + ", accountReferences=" + accountReferences.size()
                + "}";
    }
}
