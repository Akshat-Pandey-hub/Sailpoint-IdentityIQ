package com.keyforge.iiq.objectowner;

/**
 * A row of the project-owned {@code kf_object_owner} migration table — the PDF's
 * polymorphic ownership edge: {@code (object_type, object_id) -> owner}, with an
 * {@code ownership_role}. Only the {@code owner} role is currently sourceable (SCIM
 * exposes an owner on Applications, Roles and Entitlements); revoker/remediator/certifier
 * have no authoritative source yet and are therefore never emitted.
 *
 * <p>A row exists only when the object actually has an owner in the source; an object with
 * no owner produces no edge (absent, not a fabricated NULL owner).
 *
 * @param ownerid          deterministic UUID of (object_type|object_id|ownership_role|owner_id)
 * @param objectType       {@code Application} | {@code Role} | {@code Entitlement}
 * @param objectId         the owned object's id (canonical UUID)
 * @param objectName       the owned object's display name
 * @param ownershipRole    {@code owner} (the only role with a verified source)
 * @param ownerId          the owner's id (canonical UUID)
 * @param ownerDisplayName the owner's display name
 */
public record ObjectOwnerRow(
        String ownerid,
        String objectType,
        String objectId,
        String objectName,
        String ownershipRole,
        String ownerId,
        String ownerDisplayName) {
}
