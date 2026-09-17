package com.keyforge.iiq.objectowner;

import com.keyforge.iiq.model.Application;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety invariant for the kf_object_owner deletion sweep: the keep-set is rebuilt with the SAME pure
 * {@code ObjectOwnerPersistenceService.buildRows(...)} that persistence uses, and each {@code ownerid}
 * is the deterministic hash of the row's own fields — so the sweep keep-set equals the persisted
 * ownerids exactly. Objects with no owner produce no row (never persisted → never falsely deleted).
 */
class ObjectOwnerDeletionKeyTest {

    private static final String APP = "7f0001019eb91d3c819f01f0c4127efb";
    private static final String OWNER = "7f00010198421229819849ebca370c93";

    private static Application app(String id, String ownerValue) {
        Application.Owner owner = ownerValue == null ? null : new Application.Owner(ownerValue, null, "Owner Name");
        return new Application(id, "AppName", "DB", owner, null, null);
    }

    @Test
    void ownerIdIsTheDeterministicHashOfStoredFields() {
        List<ObjectOwnerRow> rows = ObjectOwnerPersistenceService.buildRows(
                List.of(app(APP, OWNER)), List.of(), List.of());
        assertEquals(1, rows.size());
        ObjectOwnerRow r = rows.get(0);
        assertEquals(ObjectOwnerRowMapper.deterministicId(r.objectType(), r.objectId(), r.ownershipRole(), r.ownerId()),
                r.ownerid(), "ownerid must be deterministicId(objectType, objectId, ownershipRole, ownerId)");
    }

    @Test
    void keepSetIsStableAcrossReDerivation() {
        // the persist run and the later sweep run must produce identical keep-sets
        String first = ObjectOwnerPersistenceService.buildRows(List.of(app(APP, OWNER)), List.of(), List.of())
                .get(0).ownerid();
        String second = ObjectOwnerPersistenceService.buildRows(List.of(app(APP, OWNER)), List.of(), List.of())
                .get(0).ownerid();
        assertEquals(first, second);
        // a different owner yields a different edge id (so an owner change deletes the old edge)
        assertNotEquals(first, ObjectOwnerPersistenceService.buildRows(
                List.of(app(APP, "7f0001019eb91d3c819f0000000000aa")), List.of(), List.of()).get(0).ownerid());
    }

    @Test
    void objectWithoutOwnerProducesNoKeepSetRow() {
        List<ObjectOwnerRow> rows = ObjectOwnerPersistenceService.buildRows(
                List.of(app(APP, null)), List.of(), List.of());
        assertTrue(rows.isEmpty(), "no owner -> no edge -> not persisted and not in the keep-set (no false deletion)");
    }
}
