package com.keyforge.iiq.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.List;

/**
 * A SailPoint IdentityIQ <b>User Group</b> (SCIM {@code Group} resource), covering
 * the User_Group requirement: all types of user groups (Workgroup / Population /
 * Group). Modelled like the other source objects — a few typed core fields plus the
 * <b>complete raw remainder</b> preserved verbatim so nothing is lost.
 *
 * <p>The group's {@link #getType() type} is kept as the raw source string (e.g.
 * "workgroup", "population", "group") rather than a fixed enum, so the three types
 * stay distinct exactly as the source reports them and are never reinterpreted.
 */
public final class UserGroup {

    private final String id;
    private final String name;
    private final String type;
    private final String description;
    private final Ref owner;

    /** Member references as returned by the source; never null. */
    private final List<Ref> members;

    /** Whether the source resource carried a {@code members} array at all. */
    private final boolean membersProvided;

    /** A Population/Group rule/criteria definition when the source carries one; else null. */
    private final JsonNode rule;

    /** Raw source status when present; null otherwise (never derived). */
    private final String status;

    private final Meta meta;

    /** All non-core attributes, preserved in their original JSON structure; never null. */
    private final ObjectNode additionalAttributes;

    public UserGroup(String id,
                     String name,
                     String type,
                     String description,
                     Ref owner,
                     List<Ref> members,
                     boolean membersProvided,
                     JsonNode rule,
                     String status,
                     Meta meta,
                     ObjectNode additionalAttributes) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.owner = owner;
        this.members = members == null ? Collections.emptyList() : List.copyOf(members);
        this.membersProvided = membersProvided;
        this.rule = rule;
        this.status = status;
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

    /** Raw source group type string (e.g. "workgroup"/"population"/"group"), or null. */
    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Ref getOwner() {
        return owner;
    }

    /** Unmodifiable list of member references (possibly empty). */
    public List<Ref> getMembers() {
        return members;
    }

    /** True only when the source resource actually carried a member list. */
    public boolean isMembersProvided() {
        return membersProvided;
    }

    public JsonNode getRule() {
        return rule;
    }

    public boolean hasRule() {
        return rule != null && !rule.isNull() && !rule.isMissingNode();
    }

    public String getStatus() {
        return status;
    }

    public Meta getMeta() {
        return meta;
    }

    /** Non-core attributes preserved verbatim (never null; empty when none). */
    public ObjectNode getAdditionalAttributes() {
        return additionalAttributes;
    }

    @Override
    public String toString() {
        return "UserGroup{id=" + id + ", name=" + name + ", type=" + type
                + ", members=" + members.size() + "}";
    }

    /** A SCIM complex reference (owner or member): {@code value}, {@code $ref}, {@code display}. */
    public static final class Ref {
        private final String value;
        private final String ref;
        private final String display;

        public Ref(String value, String ref, String display) {
            this.value = value;
            this.ref = ref;
            this.display = display;
        }

        public String getValue() {
            return value;
        }

        public String getRef() {
            return ref;
        }

        public String getDisplay() {
            return display;
        }

        @Override
        public String toString() {
            return "Ref{value=" + value + ", display=" + display + "}";
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
