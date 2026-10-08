package com.keyforge.nativeiiq.mapper;

import com.keyforge.nativeiiq.model.NativePolicyRow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import sailpoint.object.Policy;
import sailpoint.object.Rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the native SailPoint-side wiring of the Policy mapper against the real 8.4 {@code identityiq.jar}:
 * getViolationOwnerType() (None/Identity/Manager/Rule), isTemplate(), and getViolationOwnerRule() (id/name)
 * are read verbatim onto the row. Self-skips when the full IIQ runtime is absent (compiles only under the
 * {@code native} profile).
 */
class NativePolicyMapperTest {

    @Test
    void mapsViolationOwnerTypeRuleAndTemplate() {
        try {
            Policy p = new Policy();
            p.setName("SOD Template");
            p.setType("SOD");
            p.setTemplate(true);
            p.setViolationOwnerType(Policy.ViolationOwnerType.Rule);
            Rule rule = new Rule();
            rule.setName("SOD Owner Rule");
            p.setViolationOwnerRule(rule);

            NativePolicyRow row = NativePolicyMapper.map(p, "IdentityIQ", "run-1");

            assertEquals("Rule", row.getViolationOwnerType());
            assertEquals("SOD Owner Rule", row.getViolationOwnerRuleName());
            assertEquals(Boolean.TRUE, row.getTemplate());
        } catch (LinkageError e) {
            Assumptions.abort("Requires the full IdentityIQ runtime; compiles against 8.4 identityiq.jar: " + e);
        }
    }

    @Test
    void nonTemplatePolicyWithoutOwnerRuleLeavesRuleFieldsNull() {
        try {
            Policy p = new Policy();
            p.setName("Live SOD");
            p.setType("SOD");
            p.setTemplate(false);
            p.setViolationOwnerType(Policy.ViolationOwnerType.Manager);

            NativePolicyRow row = NativePolicyMapper.map(p, "IdentityIQ", "run-1");

            assertEquals("Manager", row.getViolationOwnerType());
            assertEquals(Boolean.FALSE, row.getTemplate());
            assertNull(row.getViolationOwnerRuleId(), "no owner rule when type is Manager");
            assertNull(row.getViolationOwnerRuleName());
        } catch (LinkageError e) {
            Assumptions.abort("Requires the full IdentityIQ runtime; compiles against 8.4 identityiq.jar: " + e);
        }
    }
}
