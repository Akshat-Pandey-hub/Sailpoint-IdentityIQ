package com.keyforge.nativeiiq.mapper;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import sailpoint.object.ProvisioningPlan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redaction-rule unit test for the native ProvisioningTransaction mapper. {@code secretName} is pure
 * String logic (no IIQ runtime needed), so it verifies which attribute-request names get their values
 * redacted before persistence. Compiles under the {@code native} profile alongside the mapper.
 */
class NativeProvisioningTxnMapperTest {

    @Test
    void redactsSecretAttributeNames() {
        assertTrue(NativeProvisioningTxnMapper.secretName("password"));
        assertTrue(NativeProvisioningTxnMapper.secretName("Password"));
        assertTrue(NativeProvisioningTxnMapper.secretName("currentPassword"));
        assertTrue(NativeProvisioningTxnMapper.secretName("newPassword"));
        assertTrue(NativeProvisioningTxnMapper.secretName("apiSecret"));
        assertTrue(NativeProvisioningTxnMapper.secretName("authToken"));
        assertTrue(NativeProvisioningTxnMapper.secretName("credential"));
        assertTrue(NativeProvisioningTxnMapper.secretName("privateKey"));
    }

    @Test
    void doesNotRedactOrdinaryAttributeNames() {
        assertFalse(NativeProvisioningTxnMapper.secretName("memberOf"));
        assertFalse(NativeProvisioningTxnMapper.secretName("department"));
        assertFalse(NativeProvisioningTxnMapper.secretName("email"));
        assertFalse(NativeProvisioningTxnMapper.secretName(null));
    }

    @Test
    void sourceSecretSignalAndSensitiveNameRedactBeforeValueConversion() {
        assertEquals("<redacted>", NativeProvisioningTxnMapper.safeRequestValue("cleartext", true, "department"));
        assertEquals("<redacted>", NativeProvisioningTxnMapper.safeRequestValue("cleartext", false, "password"));
        assertEquals("normal", NativeProvisioningTxnMapper.safeRequestValue("normal", false, "department"));
    }

    @Test
    void preservesJsonSafeStructuresAndDropsUnsupportedObjects() throws Exception {
        assertEquals("[\"a\",\"b\"]", json(NativeProvisioningTxnMapper.safeRequestValue(
                java.util.Arrays.asList("a", "b"), false, "groups")));
        java.util.Map<String, Object> nested = new java.util.LinkedHashMap<String, Object>();
        nested.put("team", "eng");
        nested.put("password", "hidden");
        assertEquals("{\"password\":\"<redacted>\",\"team\":\"eng\"}", json(
                NativeProvisioningTxnMapper.safeRequestValue(nested, false, "metadata")));
        assertNull(NativeProvisioningTxnMapper.safeRequestValue(null, false, "department"));
        assertNull(NativeProvisioningTxnMapper.safeRequestValue(new Object(), false, "department"));
    }

    @Test
    void usesIdentityIqSecretIndicatorsForAttributeAndPermissionRequests() {
        try {
            ProvisioningPlan.AttributeRequest attribute = new ProvisioningPlan.AttributeRequest();
            attribute.setName("password");
            attribute.setValue("must-not-escape");
            assertTrue(attribute.isSecret());
            assertEquals("<redacted>", NativeProvisioningTxnMapper.safeRequestValue(
                    attribute.getValue(), attribute.isSecret() || ProvisioningPlan.isSecret(attribute.getName()),
                    attribute.getName()));

            ProvisioningPlan.PermissionRequest permission = new ProvisioningPlan.PermissionRequest();
            permission.setTarget("password");
            permission.setRights("must-not-escape");
            // In the inspected 8.4 bytecode PermissionRequest.isSecret() returns false; use the native
            // ProvisioningPlan secret-name API on its target as the effective fallback.
            assertEquals("<redacted>", NativeProvisioningTxnMapper.safeRequestValue(
                    permission.getRights(), permission.isSecret() || ProvisioningPlan.isSecret(permission.getTarget()),
                    permission.getTarget()));
        } catch (LinkageError e) {
            Assumptions.abort("Constructing IIQ request objects requires the full runtime: " + e.getClass().getName());
        }
    }

    private static String json(Object value) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
    }
}
