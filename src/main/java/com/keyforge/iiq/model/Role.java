package com.keyforge.iiq.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A SailPoint IdentityIQ Role (SCIM {@code Role} resource, schema
 * {@code urn:ietf:params:scim:schemas:sailpoint:1.0:Role}), limited to the fields
 * actually observed on the live {@code /scim/v2/Roles} response.
 *
 * <p>Verified fields (see the Phase-2 discovery evidence): {@code id}, {@code name},
 * {@code displayableName}, {@code type} (a small object whose {@code name}/{@code displayName}
 * give the role type, e.g. business), {@code active}, {@code owner}, {@code descriptions},
 * {@code activationDate}, {@code deactivationDate}, {@code classifications}, {@code meta},
 * and the role→role relationship arrays {@code inheritance}, {@code requirements},
 * {@code permits}. The SCIM Role resource does NOT expose profiles/entitlements/applications,
 * so those are deliberately absent here — nothing is invented.
 */
public final class Role {

    private final String id;
    private final String name;
    private final String displayableName;
    private final Type type;
    private final Boolean active;
    private final Ref owner;
    /** Raw {@code descriptions} array (locale/value objects), preserved verbatim; may be null. */
    private final JsonNode descriptions;
    private final String activationDate;
    private final String deactivationDate;
    /** Raw {@code classifications} array, preserved verbatim; may be null. */
    private final JsonNode classifications;
    private final Meta meta;
    /** role→role edges; never null (empty when the response carries none). */
    private final List<Ref> inheritance;
    private final List<Ref> requirements;
    private final List<Ref> permits;

    public Role(String id, String name, String displayableName, Type type, Boolean active, Ref owner,
                JsonNode descriptions, String activationDate, String deactivationDate, JsonNode classifications,
                Meta meta, List<Ref> inheritance, List<Ref> requirements, List<Ref> permits) {
        this.id = id;
        this.name = name;
        this.displayableName = displayableName;
        this.type = type;
        this.active = active;
        this.owner = owner;
        this.descriptions = descriptions;
        this.activationDate = activationDate;
        this.deactivationDate = deactivationDate;
        this.classifications = classifications;
        this.meta = meta;
        this.inheritance = inheritance == null ? List.of() : inheritance;
        this.requirements = requirements == null ? List.of() : requirements;
        this.permits = permits == null ? List.of() : permits;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayableName() {
        return displayableName;
    }

    public Type getType() {
        return type;
    }

    public Boolean getActive() {
        return active;
    }

    public Ref getOwner() {
        return owner;
    }

    public JsonNode getDescriptions() {
        return descriptions;
    }

    public String getActivationDate() {
        return activationDate;
    }

    public String getDeactivationDate() {
        return deactivationDate;
    }

    public JsonNode getClassifications() {
        return classifications;
    }

    public Meta getMeta() {
        return meta;
    }

    public List<Ref> getInheritance() {
        return inheritance;
    }

    public List<Ref> getRequirements() {
        return requirements;
    }

    public List<Ref> getPermits() {
        return permits;
    }

    @Override
    public String toString() {
        return "Role{id=" + id + ", name=" + name + ", type=" + (type == null ? null : type.getName())
                + ", active=" + active + "}";
    }

    /** A reference to another object: {@code value} (id), {@code $ref} URI, {@code displayName}. */
    public static final class Ref {
        private final String value;
        private final String ref;
        private final String displayName;

        public Ref(String value, String ref, String displayName) {
            this.value = value;
            this.ref = ref;
            this.displayName = displayName;
        }

        public String getValue() {
            return value;
        }

        public String getRef() {
            return ref;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return "Ref{value=" + value + ", displayName=" + displayName + "}";
        }
    }

    /** The role {@code type} object; {@code name}/{@code displayName} identify the role type. */
    public static final class Type {
        private final String name;
        private final String displayName;

        public Type(String name, String displayName) {
            this.name = name;
            this.displayName = displayName;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
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
    }
}
