package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeWorkgroupRow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import sailpoint.object.Identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper unit tests. Compiles against identityiq.jar under the {@code native} profile (verifying every
 * SailPoint signature the mapper uses). Instantiating a {@code sailpoint.object.Identity} needs the
 * full IIQ runtime, so outside IIQ these self-skip on {@code LinkageError} rather than fail.
 */
class NativeWorkgroupMapperTest {

    @Test
    void mapsWorkgroupIdentityScalars() {
        try {
            Identity wg = new Identity();
            wg.setName("Security Admins");
            wg.setWorkgroup(true);

            NativeWorkgroupRow row = NativeWorkgroupMapper.map(wg, "IdentityIQ", "run-1");

            assertEquals("Security Admins", row.getName());
            assertEquals(Boolean.TRUE, row.getWorkgroup());
            assertEquals("IdentityIQ", row.getSrcSystem());
            assertEquals("run-1", row.getExtractionRunId());
            assertEquals("native_iiq_java_api", row.getSrcInterface());
            assertEquals("sailpoint.object.Identity", row.getSrcObjectType());
            // nothing invented: unset references stay null/empty
            assertNull(row.getOwnerId());
            assertTrue(row.getCapabilities().isEmpty());
            assertTrue(row.getAttributes().isEmpty());
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime (identityiq.jar alone lacks AspectJ/Hibernate/etc.); "
                    + "signatures verified by compilation, mapping validated inside IIQ: " + e);
        }
    }
}
