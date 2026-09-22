package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeManagedAttributeRow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import sailpoint.object.ManagedAttribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper unit tests. Compiles against identityiq.jar under the {@code native} profile, which verifies
 * every SailPoint signature the mapper uses. Instantiating a {@code sailpoint.object.ManagedAttribute}
 * needs the full IIQ runtime, so outside IIQ these tests self-skip (via Assumptions) on
 * {@code LinkageError} rather than fail; they run for real inside the IIQ runtime.
 */
class NativeManagedAttributeMapperTest {

    @Test
    void mapsScalarsAndLeavesUnsetRelationshipsEmpty() {
        try {
            ManagedAttribute ma = new ManagedAttribute();
            ma.setDisplayName("Admins");
            ma.setDisplayableName("Admins");

            NativeManagedAttributeRow row = NativeManagedAttributeMapper.map(ma, "IdentityIQ", "run-1");

            assertEquals("Admins", row.getDisplayName());
            assertEquals("Admins", row.getDisplayableName());
            assertEquals("IdentityIQ", row.getSrcSystem());
            assertEquals("run-1", row.getExtractionRunId());
            assertEquals("native_iiq_java_api", row.getSrcInterface());
            assertEquals("sailpoint.object.ManagedAttribute", row.getSrcObjectType());
            // nothing invented: unset references/relationships stay null/empty
            assertNull(row.getOwnerId());
            assertTrue(row.getPermissions().isEmpty());
            assertTrue(row.getInheritance().isEmpty());
            assertTrue(row.getAssociations().isEmpty());
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime (identityiq.jar alone lacks AspectJ/Hibernate/etc.); "
                    + "signatures verified by compilation, mapping validated inside IIQ: " + e);
        }
    }
}
