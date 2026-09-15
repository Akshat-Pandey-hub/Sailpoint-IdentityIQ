package com.keyforge.iiq.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.List;

/**
 * A SailPoint IdentityIQ Account (SCIM {@code Account} resource).
 *
 * <p>Accounts differ from the other resources in that their attributes vary by
 * Application (Entra, LDAP, FlatFile, ...). To avoid information loss this model
 * uses a hybrid representation:
 * <ul>
 *     <li><b>Core/common fields</b> observed on every account are strongly typed
 *         (id, displayName, nativeIdentity, active, manuallyCorrelated, locked,
 *         hasEntitlements, lastRefresh, {@link Ref application}, {@link Ref identity},
 *         {@link Meta meta}, schemas).</li>
 *     <li><b>Everything else</b> — application-specific and otherwise unknown
 *         attributes — is preserved verbatim in {@link #getAdditionalAttributes()}
 *         as a Jackson {@link ObjectNode}, keeping the original JSON structure
 *         intact: scalars stay scalars, arrays stay arrays, nested objects stay
 *         nested objects, and arrays of objects stay arrays of objects.</li>
 * </ul>
 *
 * <p>The Account&rarr;Identity and Account&rarr;Application relationships are
 * retained as typed {@link Ref} references and must not be dropped: later
 * PostgreSQL/ISPM migration depends on them.
 *
 * <p>This model deliberately does <b>not</b> derive or invent any
 * provisioning-mechanism classification (Request ID / Target Recon / Direct);
 * the raw source data is preserved so that can be investigated separately.
 */
public final class Account {

    private final String id;
    private final String displayName;
    private final String nativeIdentity;
    private final Boolean active;
    private final Boolean manuallyCorrelated;
    private final Boolean locked;
    private final Boolean hasEntitlements;
    private final String lastRefresh;
    private final Ref application;
    private final Ref identity;
    private final Meta meta;
    private final List<String> schemas;

    /** All non-core attributes, preserved in their original JSON structure; never null. */
    private final ObjectNode additionalAttributes;

    public Account(String id,
                   String displayName,
                   String nativeIdentity,
                   Boolean active,
                   Boolean manuallyCorrelated,
                   Boolean locked,
                   Boolean hasEntitlements,
                   String lastRefresh,
                   Ref application,
                   Ref identity,
                   Meta meta,
                   List<String> schemas,
                   ObjectNode additionalAttributes) {
        this.id = id;
        this.displayName = displayName;
        this.nativeIdentity = nativeIdentity;
        this.active = active;
        this.manuallyCorrelated = manuallyCorrelated;
        this.locked = locked;
        this.hasEntitlements = hasEntitlements;
        this.lastRefresh = lastRefresh;
        this.application = application;
        this.identity = identity;
        this.meta = meta;
        this.schemas = schemas == null ? Collections.emptyList() : List.copyOf(schemas);
        this.additionalAttributes = additionalAttributes == null
                ? JsonNodeFactory.instance.objectNode()
                : additionalAttributes;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The account's native identity on the source Application (e.g. a DN or login). */
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getManuallyCorrelated() {
        return manuallyCorrelated;
    }

    public Boolean getLocked() {
        return locked;
    }

    public Boolean getHasEntitlements() {
        return hasEntitlements;
    }

    public String getLastRefresh() {
        return lastRefresh;
    }

    /** Reference to the owning Application (Account &rarr; Application); may be null. */
    public Ref getApplication() {
        return application;
    }

    /** Reference to the correlated Identity (Account &rarr; Identity); may be null. */
    public Ref getIdentity() {
        return identity;
    }

    public Meta getMeta() {
        return meta;
    }

    /** Unmodifiable list of SCIM schema URIs declared on the account (possibly empty). */
    public List<String> getSchemas() {
        return schemas;
    }

    /**
     * Application-specific and otherwise non-core attributes, preserved verbatim
     * with their original JSON structure. Never null (empty object when none).
     */
    public ObjectNode getAdditionalAttributes() {
        return additionalAttributes;
    }

    /**
     * Convenience accessor for a single additional attribute by name.
     *
     * @return the raw {@link JsonNode} (which may be a scalar, array, or object),
     *         or {@code null} if the attribute is not present
     */
    public JsonNode getAdditionalAttribute(String name) {
        JsonNode value = additionalAttributes.get(name);
        return value == null ? null : value;
    }

    public boolean hasAdditionalAttributes() {
        return !additionalAttributes.isEmpty();
    }

    @Override
    public String toString() {
        return "Account{id=" + id
                + ", nativeIdentity=" + nativeIdentity
                + ", displayName=" + displayName
                + ", application=" + application
                + ", identity=" + identity
                + ", hasEntitlements=" + hasEntitlements
                + ", additionalAttributes=" + additionalAttributes.size()
                + "}";
    }

    /**
     * A SCIM complex reference. Used for both the {@code application} and
     * {@code identity} references. {@code userName} is only populated for the
     * identity reference (Applications do not carry it); it is null otherwise.
     */
    public static final class Ref {
        private final String displayName;
        private final String userName;
        private final String value;
        private final String ref;

        public Ref(String displayName, String userName, String value, String ref) {
            this.displayName = displayName;
            this.userName = userName;
            this.value = value;
            this.ref = ref;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** Identity reference only; null for application references. */
        public String getUserName() {
            return userName;
        }

        /** The referenced resource's id. */
        public String getValue() {
            return value;
        }

        /** The SCIM {@code $ref} URI for the referenced resource. */
        public String getRef() {
            return ref;
        }

        @Override
        public String toString() {
            return "Ref{value=" + value + ", displayName=" + displayName + "}";
        }
    }

    /** Standard SCIM {@code meta} block; all sub-attributes optional. */
    public static final class Meta {
        private final String resourceType;
        private final String location;
        private final String created;
        private final String lastModified;
        private final String version;

        public Meta(String resourceType, String location, String created, String lastModified, String version) {
            this.resourceType = resourceType;
            this.location = location;
            this.created = created;
            this.lastModified = lastModified;
            this.version = version;
        }

        public String getResourceType() {
            return resourceType;
        }

        public String getLocation() {
            return location;
        }

        public String getCreated() {
            return created;
        }

        public String getLastModified() {
            return lastModified;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "Meta{resourceType=" + resourceType + ", version=" + version + "}";
        }
    }
}
