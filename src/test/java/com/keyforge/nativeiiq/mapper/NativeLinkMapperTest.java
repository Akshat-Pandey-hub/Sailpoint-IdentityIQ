package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeLinkRow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import sailpoint.object.Link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper unit tests. Compiles against identityiq.jar under the {@code native} profile (verifying every
 * SailPoint signature the mapper uses). Instantiating a {@code sailpoint.object.Link} needs the full
 * IIQ runtime, so outside IIQ these self-skip on {@code LinkageError} rather than fail.
 */
class NativeLinkMapperTest {

    @Test
    void mapsScalarsAndLeavesUnsetRelationshipsEmpty() {
        try {
            Link link = new Link();
            link.setDisplayName("jsmith");

            NativeLinkRow row = NativeLinkMapper.map(link, "IdentityIQ", "run-1");

            assertEquals("jsmith", row.getDisplayName());
            assertEquals("IdentityIQ", row.getSrcSystem());
            assertEquals("run-1", row.getExtractionRunId());
            assertEquals("native_iiq_java_api", row.getSrcInterface());
            assertEquals("sailpoint.object.Link", row.getSrcObjectType());
            // nothing invented: unset references/relationships stay null/empty
            assertNull(row.getIdentityId());
            assertTrue(row.getPermissions().isEmpty());
            assertTrue(row.getTargetPermissions().isEmpty());
            assertTrue(row.getAttributes().isEmpty());
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime (identityiq.jar alone lacks AspectJ/Hibernate/etc.); "
                    + "signatures verified by compilation, mapping validated inside IIQ: " + e);
        }
    }
}
