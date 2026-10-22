package com.keyforge.iiq.rest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The debug reason given for each kind of rejected request, and that it agrees with {@code authorized}. */
class RestAuthDenyReasonTest {

    private final RestAuthFilter filter = RestAuthFilter.forClientCredentials("test-client", "test-secret-123");

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void allowsTheConfiguredPair() {
        assertNull(filter.denyReason(basic("test-client", "test-secret-123")));
        assertTrue(filter.authorized(basic("test-client", "test-secret-123")));
    }

    @Test
    void namesEachKindOfRejection() {
        assertTrue(filter.denyReason(null).startsWith("NO_CREDENTIALS"));
        assertTrue(filter.denyReason("Bearer abc").startsWith("NOT_BASIC"));
        assertTrue(filter.denyReason("Basic %%%").startsWith("BAD_ENCODING"));
        assertTrue(filter.denyReason("Basic " + Base64.getEncoder().encodeToString("nocolon".getBytes(
                StandardCharsets.UTF_8))).startsWith("BAD_FORMAT"));
        assertTrue(filter.denyReason(basic("test-client", "wrong")).startsWith("WRONG_PASSWORD"));
        assertTrue(filter.denyReason(basic("spadmin", "test-secret-123")).startsWith("WRONG_USERNAME"));
        assertTrue(filter.denyReason(basic("test-secret-123", "test-client")).startsWith("WRONG_USERNAME"));
        assertTrue(RestAuthFilter.unconfigured().denyReason(basic("a", "b")).startsWith("NOT_CONFIGURED"));
    }

    @Test
    void reasonAgreesWithTheAccessDecision() {
        String[] headers = {null, "Bearer abc", basic("test-client", "wrong"), basic("test-client", "test-secret-123"),
                basic("test-client", "test-secret-123 ")};
        for (String h : headers) {
            assertEquals(filter.authorized(h), filter.denyReason(h) == null, "header " + h);
        }
    }
}
