package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeGroupDefinitionRow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import sailpoint.object.GroupDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mapper unit tests. Compiles against identityiq.jar under the {@code native} profile (verifying every
 * SailPoint signature the mapper uses). Instantiating a {@code sailpoint.object.GroupDefinition} needs
 * the full IIQ runtime, so outside IIQ these self-skip on {@code LinkageError} rather than fail.
 */
class NativeGroupDefinitionMapperTest {

    @Test
    void noFactoryMapsToPopulation() {
        try {
            GroupDefinition gd = new GroupDefinition();
            gd.setName("Contractors");
            // no factory set

            NativeGroupDefinitionRow row = NativeGroupDefinitionMapper.map(gd, "IdentityIQ", "run-1");

            assertEquals("Contractors", row.getName());
            // a definition with no factory is a POPULATION (the authoritative native distinction)
            assertEquals(NativeGroupDefinitionMapper.TYPE_POPULATION, row.getType());
            assertNull(row.getFactoryId());
            assertEquals("IdentityIQ", row.getSrcSystem());
            assertEquals("native_iiq_java_api", row.getSrcInterface());
            assertEquals("sailpoint.object.GroupDefinition", row.getSrcObjectType());
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime (identityiq.jar alone lacks AspectJ/Hibernate/etc.); "
                    + "signatures verified by compilation, mapping validated inside IIQ: " + e);
        }
    }
}
