package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativeApplicationRow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import sailpoint.object.Application;
import sailpoint.object.Rule;
import sailpoint.object.Schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper unit tests. Compiles against identityiq.jar under the {@code native} profile (verifying every
 * SailPoint signature the mapper uses). Instantiating a {@code sailpoint.object.Application} needs the
 * full IIQ runtime, so outside IIQ these self-skip on {@code LinkageError} rather than fail.
 */
class NativeApplicationMapperTest {

    @Test
    void mapsScalarsAndLeavesUnsetRelationshipsEmpty() {
        try {
            Application app = new Application();
            app.setName("Active Directory");

            NativeApplicationRow row = NativeApplicationMapper.map(app, "IdentityIQ", "run-1");

            assertEquals("Active Directory", row.getName());
            assertEquals("IdentityIQ", row.getSrcSystem());
            assertEquals("run-1", row.getExtractionRunId());
            assertEquals("native_iiq_java_api", row.getSrcInterface());
            assertEquals("sailpoint.object.Application", row.getSrcObjectType());
            // nothing invented: unset references/relationships stay null/empty
            assertNull(row.getOwnerId());
            assertTrue(row.getSecondaryOwners().isEmpty());
            assertTrue(row.getRemediators().isEmpty());
            assertTrue(row.getDependencies().isEmpty());
            assertTrue(row.getSchemas().isEmpty());
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime (identityiq.jar alone lacks AspectJ/Hibernate/etc.); "
                    + "signatures verified by compilation, mapping validated inside IIQ: " + e);
        }
    }

    @Test
    void mapsRuleReferencesAsSourceIdAndNameWithoutSerializingRuleBodies() {
        try {
            Application app = new Application();
            app.setName("AD");
            Rule appCreation = new Rule();
            appCreation.setId("app-rule-id");
            appCreation.setName("AppCreationRule");
            app.setCreationRule(appCreation);

            Schema account = new Schema();
            account.setObjectType("account");
            Rule correlation = new Rule();
            correlation.setId("corr-id");
            correlation.setName("AccountCorrelation");
            account.setCorrelationRule(correlation);
            app.addSchema(account);

            NativeApplicationRow row = NativeApplicationMapper.map(app, "IdentityIQ", "run-rule");
            assertEquals("AppCreationRule", row.getApplicationCreationRule());
            assertEquals("app-rule-id", row.getApplicationCreationRuleId());
            assertEquals("AccountCorrelation", row.getAccountSchemaCorrelationRule());
            assertEquals("corr-id", row.getAccountSchemaCorrelationRuleId());
        } catch (LinkageError e) {
            Assumptions.abort("Requires full IIQ runtime (identityiq.jar alone lacks AspectJ/Hibernate/etc.): " + e);
        }
    }
}
