package com.keyforge.iiq.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A SailPoint IdentityIQ Entitlement (SCIM {@code Entitlement} resource),
 * limited to the fields observed on the live {@code /scim/v2/Entitlements}
 * response.
 *
 * <p>Observed/mapped fields:
 * <ul>
 *     <li>{@code id}              - SCIM resource id</li>
 *     <li>{@code displayableName} - human-readable name</li>
 *     <li>{@code value}           - entitlement value</li>
 *     <li>{@code attribute}       - the account attribute this entitlement lives on
 *                                   (e.g. "groups", "posixgroups")</li>
 *     <li>{@code requestable}     - whether the entitlement can be requested</li>
 *     <li>{@code type}            - entitlement type (e.g. "group", "posixgroup")</li>
 *     <li>{@code application}     - reference back to the owning Application
 *                                   ({@link ApplicationRef})</li>
 * </ul>
 */
public final class Entitlement {

    private final String id;
    private final String displayableName;
    private final String value;
    private final String attribute;
    private final Boolean requestable;
    private final String type;
    private final ApplicationRef application;

    /** Standard SCIM {@code meta} block; may be null. Additive, non-breaking. */
    private final Meta meta;

    /**
     * The raw entitlement resource minus the fields fully captured in dedicated
     * columns ({@code id}, {@code value}, {@code displayableName}); never null.
     * Preserves everything else verbatim — including {@code type} (whose enum column
     * may reject the source value) and any field we do not explicitly model — so no
     * source information is lost.
     */
    private final ObjectNode additionalAttributes;

    public Entitlement(String id,
                       String displayableName,
                       String value,
                       String attribute,
                       Boolean requestable,
                       String type,
                       ApplicationRef application) {
        this(id, displayableName, value, attribute, requestable, type, application, null, null);
    }

    public Entitlement(String id,
                       String displayableName,
                       String value,
                       String attribute,
                       Boolean requestable,
                       String type,
                       ApplicationRef application,
                       Meta meta) {
        this(id, displayableName, value, attribute, requestable, type, application, meta, null);
    }

    public Entitlement(String id,
                       String displayableName,
                       String value,
                       String attribute,
                       Boolean requestable,
                       String type,
                       ApplicationRef application,
                       Meta meta,
                       ObjectNode additionalAttributes) {
        this.id = id;
        this.displayableName = displayableName;
        this.value = value;
        this.attribute = attribute;
        this.requestable = requestable;
        this.type = type;
        this.application = application;
        this.meta = meta;
        this.additionalAttributes = additionalAttributes == null
                ? JsonNodeFactory.instance.objectNode()
                : additionalAttributes;
    }

    public String getId() {
        return id;
    }

    public String getDisplayableName() {
        return displayableName;
    }

    public String getValue() {
        return value;
    }

    public String getAttribute() {
        return attribute;
    }

    public Boolean getRequestable() {
        return requestable;
    }

    public String getType() {
        return type;
    }

    /** Reference to the owning Application; may be null if absent from the response. */
    public ApplicationRef getApplication() {
        return application;
    }

    /** SCIM {@code meta} block; may be null. */
    public Meta getMeta() {
        return meta;
    }

    /**
     * Raw entitlement attributes not captured in dedicated columns (never null;
     * empty when the response carried nothing beyond id/value/displayableName).
     * Preserved verbatim, including {@code type}.
     */
    public ObjectNode getAdditionalAttributes() {
        return additionalAttributes;
    }

    @Override
    public String toString() {
        return "Entitlement{id=" + id
                + ", displayableName=" + displayableName
                + ", value=" + value
                + ", attribute=" + attribute
                + ", type=" + type
                + ", application=" + application
                + "}";
    }

    /**
     * Reference linking an Entitlement back to its IdentityIQ Application.
     * Observed sub-attributes: {@code displayName}, {@code value} (the Application
     * id), and {@code $ref}.
     */
    public static final class ApplicationRef {
        private final String displayName;
        private final String value;
        private final String ref;

        public ApplicationRef(String displayName, String value, String ref) {
            this.displayName = displayName;
            this.value = value;
            this.ref = ref;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** The referenced Application's id. */
        public String getValue() {
            return value;
        }

        /** The SCIM {@code $ref} URI for the Application. */
        public String getRef() {
            return ref;
        }

        @Override
        public String toString() {
            return "ApplicationRef{displayName=" + displayName + ", value=" + value + "}";
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
