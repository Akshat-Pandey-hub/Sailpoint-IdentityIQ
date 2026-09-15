package com.keyforge.iiq.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.List;

/**
 * A SailPoint IdentityIQ Application (SCIM {@code Application} resource),
 * limited to the fields observed on the live {@code /scim/v2/Applications}
 * response.
 *
 * <p>Observed/mapped fields:
 * <ul>
 *     <li>{@code id}                 - SCIM resource id</li>
 *     <li>{@code name}               - application name</li>
 *     <li>{@code type}               - application type/connector</li>
 *     <li>{@code owner}              - owner reference ({@link Owner})</li>
 *     <li>{@code applicationSchemas} - schema references ({@link ApplicationSchema})</li>
 *     <li>{@code meta}               - SCIM {@link Meta} resource metadata</li>
 * </ul>
 *
 * <p>Everything the response returns beyond the typed fields above (e.g.
 * {@code descriptions}, {@code schemas}, and any field we do not model) is preserved
 * verbatim in {@link #getAdditionalAttributes()} so no source information is lost.
 */
public final class Application {

    private final String id;
    private final String name;
    private final String type;
    private final Owner owner;
    private final List<ApplicationSchema> applicationSchemas;
    private final Meta meta;

    /** Raw resource fields not captured by the typed fields above; never null. */
    private final ObjectNode additionalAttributes;

    public Application(String id,
                       String name,
                       String type,
                       Owner owner,
                       List<ApplicationSchema> applicationSchemas,
                       Meta meta) {
        this(id, name, type, owner, applicationSchemas, meta, null);
    }

    public Application(String id,
                       String name,
                       String type,
                       Owner owner,
                       List<ApplicationSchema> applicationSchemas,
                       Meta meta,
                       ObjectNode additionalAttributes) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.applicationSchemas = applicationSchemas == null
                ? Collections.emptyList()
                : List.copyOf(applicationSchemas);
        this.meta = meta;
        this.additionalAttributes = additionalAttributes == null
                ? JsonNodeFactory.instance.objectNode()
                : additionalAttributes;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Owner getOwner() {
        return owner;
    }

    /** Unmodifiable list of application schema references (possibly empty). */
    public List<ApplicationSchema> getApplicationSchemas() {
        return applicationSchemas;
    }

    public int getApplicationSchemaCount() {
        return applicationSchemas.size();
    }

    public Meta getMeta() {
        return meta;
    }

    /**
     * Raw resource fields not captured by the typed fields (never null; empty when
     * the response carried nothing extra). Preserved verbatim, e.g. {@code descriptions},
     * {@code schemas}.
     */
    public ObjectNode getAdditionalAttributes() {
        return additionalAttributes;
    }

    @Override
    public String toString() {
        return "Application{id=" + id
                + ", name=" + name
                + ", type=" + type
                + ", owner=" + owner
                + ", applicationSchemas=" + applicationSchemas.size()
                + "}";
    }

    /**
     * Owner reference. Uses the standard SCIM complex-reference sub-attributes:
     * {@code value}, {@code $ref}, and a display label ({@code displayName}, or
     * {@code display} when that is what the server returns).
     */
    public static final class Owner {
        private final String value;
        private final String ref;
        private final String displayName;

        public Owner(String value, String ref, String displayName) {
            this.value = value;
            this.ref = ref;
            this.displayName = displayName;
        }

        public String getValue() {
            return value;
        }

        /** The SCIM {@code $ref} URI for the owner. */
        public String getRef() {
            return ref;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return "Owner{value=" + value + ", displayName=" + displayName + "}";
        }
    }

    /**
     * An entry from an Application's {@code applicationSchemas}. Observed
     * sub-attributes: {@code type} (e.g. "account"), {@code value}, {@code $ref}.
     */
    public static final class ApplicationSchema {
        private final String type;
        private final String value;
        private final String ref;

        public ApplicationSchema(String type, String value, String ref) {
            this.type = type;
            this.value = value;
            this.ref = ref;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        /** The SCIM {@code $ref} URI for the schema. */
        public String getRef() {
            return ref;
        }

        @Override
        public String toString() {
            return "ApplicationSchema{type=" + type + ", value=" + value + "}";
        }
    }

    /**
     * Standard SCIM {@code meta} block. All sub-attributes are optional; whichever
     * the server omits are left null.
     */
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
