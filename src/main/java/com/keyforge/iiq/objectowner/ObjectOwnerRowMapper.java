package com.keyforge.iiq.objectowner;

import com.fasterxml.jackson.databind.JsonNode;
import com.keyforge.iiq.model.Application;
import com.keyforge.iiq.model.Entitlement;
import com.keyforge.iiq.model.Role;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds {@code kf_object_owner} edges from the verified owner reference on each supported
 * source object. Pure and DB-free. Never invents ownership: an object with no owner (or an
 * owner without a usable id) yields {@link Optional#empty()} and no row is written.
 *
 * <ul>
 *     <li>Application — {@code owner} (typed on the model)</li>
 *     <li>Role — {@code owner} (typed on the model)</li>
 *     <li>Entitlement — {@code owner} read from the preserved raw attributes; the existing
 *         Entitlement extraction already keeps this node verbatim, so no change to that
 *         extraction is required.</li>
 * </ul>
 */
public final class ObjectOwnerRowMapper {

    public static final String OBJECT_APPLICATION = "Application";
    public static final String OBJECT_ROLE = "Role";
    public static final String OBJECT_ENTITLEMENT = "Entitlement";
    public static final String ROLE_OWNER = "owner";

    private ObjectOwnerRowMapper() {
    }

    public static Optional<ObjectOwnerRow> fromApplication(Application app) {
        if (app == null) {
            return Optional.empty();
        }
        Application.Owner owner = app.getOwner();
        String ownerValue = owner == null ? null : owner.getValue();
        String ownerDisplay = owner == null ? null : owner.getDisplayName();
        return build(OBJECT_APPLICATION, app.getId(), app.getName(), ownerValue, ownerDisplay);
    }

    public static Optional<ObjectOwnerRow> fromRole(Role role) {
        if (role == null) {
            return Optional.empty();
        }
        Role.Ref owner = role.getOwner();
        String ownerValue = owner == null ? null : owner.getValue();
        String ownerDisplay = owner == null ? null : owner.getDisplayName();
        String name = blankToNull(role.getDisplayableName()) != null ? role.getDisplayableName() : role.getName();
        return build(OBJECT_ROLE, role.getId(), name, ownerValue, ownerDisplay);
    }

    public static Optional<ObjectOwnerRow> fromEntitlement(Entitlement entitlement) {
        if (entitlement == null) {
            return Optional.empty();
        }
        // The owner is preserved verbatim in the entitlement's raw attributes (the existing
        // extraction keeps every field except id/value/displayableName). Read it there.
        JsonNode owner = entitlement.getAdditionalAttributes() == null
                ? null : entitlement.getAdditionalAttributes().path("owner");
        String ownerValue = text(owner, "value");
        String ownerDisplay = text(owner, "displayName");
        String name = blankToNull(entitlement.getDisplayableName()) != null
                ? entitlement.getDisplayableName() : entitlement.getValue();
        return build(OBJECT_ENTITLEMENT, entitlement.getId(), name, ownerValue, ownerDisplay);
    }

    /** Common builder — emits a row only when both endpoints resolve to real UUIDs. */
    private static Optional<ObjectOwnerRow> build(String objectType, String rawObjectId, String objectName,
                                                  String rawOwnerId, String ownerDisplayName) {
        String objectId = canonicalOrNull(rawObjectId);
        String ownerId = canonicalOrNull(rawOwnerId);
        if (objectId == null || ownerId == null) {
            // No owner, or an id we cannot represent as a UUID -> no edge (never fabricated).
            return Optional.empty();
        }
        String ownerid = deterministicId(objectType, objectId, ROLE_OWNER, ownerId);
        return Optional.of(new ObjectOwnerRow(
                ownerid, objectType, objectId, blankToNull(objectName), ROLE_OWNER,
                ownerId, blankToNull(ownerDisplayName)));
    }

    static String deterministicId(String objectType, String objectId, String ownershipRole, String ownerId) {
        String key = "ObjectOwner|" + objectType + "|" + objectId + "|" + ownershipRole + "|" + ownerId;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String canonicalOrNull(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String s = rawId.trim();
        if (s.startsWith("{") && s.endsWith("}") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        String hex = s.replace("-", "");
        try {
            if (hex.length() == 32 && hex.matches("[0-9a-fA-F]{32}")) {
                String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                        + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
                return UUID.fromString(dashed).toString();
            }
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
