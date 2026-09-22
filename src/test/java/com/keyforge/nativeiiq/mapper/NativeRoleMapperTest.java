package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeRoleRow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import sailpoint.object.Bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper unit tests. Compiles against identityiq.jar under the {@code native} profile (verifying every
 * SailPoint signature the mapper uses). Instantiating a {@code sailpoint.object.Bundle} needs the full
 * IIQ runtime, so outside IIQ these self-skip on {@code LinkageError} rather than fail.
 */
class NativeRoleMapperTest {

    @Test
    void mapsScalarsAndLeavesUnsetRelationshipsEmpty() {
        try {
            Bundle bundle = new Bundle();
            bundle.setName("Engineer");
            bundle.setDisplayName("Engineer");

            NativeRoleRow row = NativeRoleMapper.map(bundle, "IdentityIQ", "run-1");

            assertEquals("Engineer", row.getName());
            assertEquals("Engineer", row.getDisplayName());
            assertEquals("IdentityIQ", row.getSrcSystem());
            assertEquals("run-1", row.getExtractionRunId());
            assertEquals("native_iiq_java_api", row.getSrcInterface());
            assertEquals("sailpoint.object.Bundle", row.getSrcObjectType());
            // nothing invented: unset references stay null/empty
            assertNull(row.getOwnerId());
            assertTrue(row.getAttributes().isEmpty());
            assertTrue(row.getDescriptions().isEmpty());
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime (identityiq.jar alone lacks AspectJ/Hibernate/etc.); "
                    + "signatures verified by compilation, mapping validated inside IIQ: " + e);
        }
    }
}
